(*  Title:      Auto_Isabelle.ML
    Author:     Zenan Li and Baoding He
    Description: Try a combination of proof methods. Tweak from the try0.ML.
                 In Scala env, timeout mechanism is not working.
*)

structure Auto_Isabelle : sig
  val clean_theorem_text : string -> string
  type result = {name: string, command: string, time: int, state: Proof.state}
  val try_close : Time.time -> Proof.state -> bool * string * string
  val try_nitpick: Time.time -> Toplevel.state -> theory -> bool * string
  val try_quickcheck: Time.time -> Toplevel.state -> theory -> bool * string
  val find_theorems: Toplevel.state -> string list -> int option -> bool -> string
  val explicitize_proof: Toplevel.state -> string -> bool * string * string
  val retrieve_facts : Proof.state -> theory -> string -> string list -> string list -> string 
  val retrieve_facts_with_theory_names : Proof.state -> theory -> string -> string list -> string list -> string
  val mash_relearn : Proof.state -> theory -> unit
end = struct

(* ============================================================================
   Utility functions
   ============================================================================ *)

fun clean_theorem_text thm_text = 
  XML.content_of (YXML.parse_body thm_text);

val num_goals = Thm.nprems_of o #goal o Proof.goal;

(* List of proof methods to try *)
val named_methods =
  ["simp", "auto", "blast", "argo", "linarith", "presburger",
   "algebra", "fast", "fastforce", "force", "meson", "satx", "order"];

(* Parse a method string into a method *)
fun parse_method ctxt s =
  enclose "(" ")" s
  |> Token.explode (Thy_Header.get_keywords' ctxt) Position.start
  |> filter Token.is_proper
  |> Scan.read Token.stopper Method.parse
  |> (fn SOME (Method.Source src, _) => src | _ => raise Fail "expected Source");

(* Apply a tactic and return timing information *)
fun simple_apply tac st =
  let
    val result = try (fn () => Seq.pull (tac st)) ()
  in
    case Option.join result of
      SOME (st', _) => (Time.zeroTime, st')
    | NONE => (Time.zeroTime, st)
  end;

(* Create antiquotation markup *)
fun antiquot s = "\\" ^ "<open>" ^ s ^ "\\" ^ "<close>";

(* ============================================================================
   Result type definition
   ============================================================================ *)

type result = {name: string, command: string, time: int, state: Proof.state};

(* ============================================================================
   Method application
   ============================================================================ *)

(* Apply a named method to the current proof state *)
fun apply_named_method name _ st =
  let
    val ctxt = Proof.context_of st;
    val local_facts = Facts.props (Proof_Context.facts_of ctxt) |> map #1 |> take 10; 
    val text =
      name
      |> parse_method ctxt
      |> Method.method_cmd ctxt
      |> Method.Basic
      |> (fn m => Method.Combinator (Method.no_combinator_info, Method.Select_Goals 1, [m]));
    val apply =
      Proof.using [local_facts |> map (rpair [] o single)]
      #> Proof.refine text #> Seq.filter_results;
    val (time, st') = simple_apply apply st;
    val num_after = num_goals st';
    val command =
      (if null local_facts then "" 
       else "using " ^ space_implode " " 
            (map (fn thm => antiquot (Thm.string_of_thm ctxt thm)) local_facts) ^ " ") ^
      (if num_after = 0 then "by " else "") ^ name
  in
    if num_after = 0 then
      SOME {name = name, command = command, time = Time.toMilliseconds time, state = st'}
    else
      NONE
  end;

(* ============================================================================
   Asynchronous proof attempts
   ============================================================================ *)

(* Try to close the proof asynchronously using multiple methods *)
fun try_close_async st =
  Future.fork (fn () =>
    let
      val apply_methods = map apply_named_method named_methods;
      val results =
        map (fn method =>
          case method (SOME (seconds 0.0)) st of
            SOME r => SOME r
          | NONE => NONE
        ) apply_methods
        |> List.mapPartial I
    in
      case results of
        [] => (false, "All Failed", "")
      | {name, command, ...} :: _ =>
          (true, name, "Try this: " ^ Active.sendback_markup_command command)
    end);

(* Try to close the proof with timeout *)
fun try_close timeout st =
  let
    val future = try_close_async st;
    val start_time = Time.now ();
    fun check_timeout () = Time.now () - start_time > timeout;
    fun poll interval =
      if Future.is_finished future then
        Future.join future
      else if check_timeout () then
        (Future.cancel future; (false, "All tactics timed out", ""))
      else
        (OS.Process.sleep (seconds interval); poll (Real.min ((interval * 2.0), 1.0)))
  in
    poll 0.1
  end;

(* Try to refuse the proof asynchronously using nitpick *)
fun try_nitpick_async st thy =
  Future.fork (fn () =>
    let
    val proof_state = Toplevel.proof_of st;
    val step = Toplevel.proof_position_of st;

    (* val {context = _, facts, goal} = Proof.goal proof_state; *)

    val params = Nitpick_Commands.default_params thy
                   [("show_all", "true"), ("timeout", "60")];

    val (str_result, _) = Nitpick.pick_nits_in_subgoal
                            proof_state params Nitpick.Normal 1 step;
    in
      (true, str_result)
    end);

(* Try to nitpick the proof with timeout *)
fun try_nitpick timeout st thy =
  let
    val future = try_nitpick_async st thy;
    val start_time = Time.now ();
    fun check_timeout () = Time.now () - start_time > timeout;
    fun poll interval =
      if Future.is_finished future then
        Future.join future
      else if check_timeout () then
        (Future.cancel future; (false, "Nitpick timed out"))
      else
        (OS.Process.sleep (seconds interval); poll (Real.min ((interval * 2.0), 1.0)))
  in
    poll 0.1
  end;

(* Run Quickcheck asynchronously on subgoal 1.
   timeout: Time.time — internal timeout passed to Quickcheck, also used for external watchdog
   st: Toplevel.state
   thy: theory (kept for compatibility with the Nitpick version, but not used)
   Returns: (bool * string) — (success flag, formatted Quickcheck output)
 *)
fun try_quickcheck_async timeout st _ =
  Future.fork (fn () =>
    let
      val state = Toplevel.proof_of st

      (* Configure Quickcheck parameters inside the proof context:
         - disable report output
         - enable quiet mode
         - set desired number of iterations
         - set Quickcheck’s internal timeout (expects seconds as real)
       *)
      val state' =
        state
        |> Proof.map_context
            (Config.put Quickcheck.report false
             #> Config.put Quickcheck.quiet  true
             #> Config.put Quickcheck.iterations 1000
             #> Config.put Quickcheck.timeout (Time.toReal timeout))

      (* Run Quickcheck on subgoal 1; result is:
         (bool * (string * term) list) option
         Some counterexample  → found
         None                 → no counterexample
       *)
      val cex_opt = (Quickcheck.quickcheck [] 1 state')

      fun simple_pretty_counterex ctxt auto result =
  case result of
      NONE =>
        Pretty.str "Quickcheck found no counterexample."
    | SOME ((genuine, _), _) =>
        Pretty.str ("Quickcheck found a " ^
          (if genuine then "counterexample."
           else "potentially spurious counterexample due to underspecified functions."))



      (* Format the result using Quickcheck’s pretty printer.
         Quickcheck.pretty_counterex expects:
           SOME ((bool * (string * term) list), (term * term) list)
         We give an empty evaluation-term list.
       *)
      val ctxt = Proof.context_of state'
      val msg =
        Pretty.string_of
          (simple_pretty_counterex ctxt true
             (Option.map (fn cex => (cex, [])) cex_opt))
    in
      (true, msg)
    end)


(* Run Quickcheck with an external watchdog timeout.
   If the asynchronous Quickcheck task does not finish within the
   given timeout, cancel the Future and return a timeout message.
 *)
fun try_quickcheck timeout st thy =
  let
    val future = try_quickcheck_async timeout st thy
    val start_time = Time.now ()

    fun timeout_exceeded () = Time.now () - start_time > timeout

    (* Poll the Future until:
         - it finishes
         - or the timeout expires
       Poll interval doubles up to 1 second to reduce overhead.
     *)
    fun poll interval =
      if Future.is_finished future then
        Future.join future
      else if timeout_exceeded () then
        (Future.cancel future; (false, "Quickcheck timed out"))
      else
        (OS.Process.sleep (seconds interval);
         poll (Real.min (interval * 2.0, 1.0)))
  in
    poll 0.1
  end

(* Find theorems that match a list of term-pattern criteria.
   query_patterns: each string corresponds to one positive pattern criterion,
                   e.g. "obj_at _ _" or "set_thread_state"
   opt_limit: NONE means Isabelle default limit; SOME n limits display to n
   rem_dups: true removes duplicate theorem bodies from results
 *)
fun find_theorems st query_patterns opt_limit rem_dups =
  let
    val state = Find_Theorems.proof_state st;
    val criteria = map (fn pat => (true, Find_Theorems.Pattern pat)) query_patterns;
    val pretty = Find_Theorems.pretty_theorems state opt_limit rem_dups criteria;
  in
    clean_theorem_text (Pretty.string_of pretty)
  end;


(* Build an explicit replay command for simp/auto by tracing simplifier rewrites. *)
fun with_tracing_capture f =
  let
    val trace_buffer = Unsynchronized.ref ([]: string list);
    val tracing_backup = !Private_Output.tracing_fn;

    fun collect outputs =
      let
        val text = clean_theorem_text (String.concat outputs);
      in
        if text = "" then ()
        else trace_buffer := text :: !trace_buffer
      end;

    val _ = Private_Output.tracing_fn := collect;
    val result = Exn.capture f ();
    val _ = Private_Output.tracing_fn := tracing_backup;
  in
    (result, rev (!trace_buffer))
  end;

fun normalize_trace_lines lines =
  lines
  |> maps (fn text => String.fields (fn c => c = #"\n" orelse c = #"\r") text)
  |> filter_out (fn line => line = "");

val rewrite_prefix = "Applying instance of rewrite rule ";

fun extract_rewrite_name line =
  let
    val (_, suffix) = Substring.position rewrite_prefix (Substring.full line);
  in
    if Substring.isEmpty suffix then NONE
    else
      let
        val rest =
          suffix
          |> Substring.triml (size rewrite_prefix)
          |> Substring.string;
        val candidate =
          if String.isPrefix "\"" rest then
            (case String.fields (fn c => c = #"\"") rest of
              _ :: x :: _ => x
            | _ => "")
          else
            (case String.fields (fn c => c = #":") rest of
              [] => ""
            | x :: _ => x);
        val name =
          candidate
          |> String.tokens (fn c => c = #" " orelse c = #"\t")
          |> space_implode " ";
      in
        if name = "" then NONE else SOME name
      end
  end;

fun dedup_preserve xs =
  fold (fn x => fn acc => if member (op =) acc x then acc else acc @ [x]) xs [];

fun local_fact_prefix ctxt local_facts =
  if null local_facts then ""
  else
    "using " ^
    space_implode " " (map (fn thm => antiquot (Thm.string_of_thm ctxt thm)) local_facts) ^
    " ";

fun format_command using_prefix solved method_body =
  using_prefix ^ (if solved then "by " else "apply ") ^ "(" ^ method_body ^ ")";

(* Multi-step output format: first step carries the `using` prefix; middle
   steps are `apply (…)`; the last step is `by (…)` iff the original method
   closed the goal. The <\CMD_SEP> delimiter is parsed apart on the Python
   side into a list of commands.

   Commands are expected to be applied IN ORDER and the ML side has already
   verified that the sequence reaches the same end-state as the original
   method via `sequence_same_state_as`. *)
val cmd_sep = "<\\CMD_SEP>";

fun format_command_sequence using_prefix solved method_bodies =
  let
    val n = length method_bodies;
    fun fmt i body =
      let
        val is_first = i = 0;
        val is_last = i = n - 1;
        val prefix = if is_first then using_prefix else "";
        val verb = if is_last andalso solved then "by " else "apply ";
      in
        prefix ^ verb ^ "(" ^ body ^ ")"
      end;
  in
    space_implode cmd_sep (map_index (uncurry fmt) method_bodies)
  end;

fun run_method_on_state state method_body local_facts =
  let
    val ctxt = Proof.context_of state;
    val text =
      method_body
      |> parse_method ctxt
      |> Method.method_cmd ctxt
      |> Method.Basic
      |> (fn m => Method.Combinator (Method.no_combinator_info, Method.Select_Goals 1, [m]));
    val apply =
      Proof.using [local_facts |> map (rpair [] o single)]
      #> Proof.refine text
      #> Seq.filter_results;
    val (_, st') = simple_apply apply state;
  in
    (st', num_goals st' = 0)
  end;

(* Fold a list of method bodies through the state. `using` facts only attach
   to the first step (matching how Isabelle's `using` threads through the
   proof). Returns final state and whether the last step closed the goal,
   or NONE if any step raised / failed to make progress on a non-final
   subgoal structure. *)
fun run_method_sequence state method_bodies local_facts =
  let
    fun step (body, i) st =
      let
        val facts = if i = 0 then local_facts else [];
        val (st', solved) = run_method_on_state st body facts;
      in
        (st', solved)
      end;
    fun loop _ [] st solved = (st, solved)
      | loop i (b :: bs) st _ =
          let val (st', solved') = step (b, i) st
          in loop (i + 1) bs st' solved' end;
  in
    loop 0 method_bodies state false
  end;

fun explicitize_proof st method_name =
  let
    val method = if method_name = "" then "simp" else method_name;
    val supported = member (op =) ["simp", "auto"] method;
  in
    if not supported then
      (false, "", "Unsupported method for explicitization: " ^ method ^ ". Supported: simp, auto")
    else
      let
        val proof_state = Toplevel.proof_of st;
        val ctxt = Proof.context_of proof_state;
        val local_facts = Facts.props (Proof_Context.facts_of ctxt) |> map #1 |> take 10;
        val using_prefix = local_fact_prefix ctxt local_facts;

        val traced_state =
          proof_state
          |> Proof.map_context
              (Config.put Raw_Simplifier.simp_trace true
               #> Config.put Raw_Simplifier.simp_trace_depth_limit 20
               #> Config.put Raw_Simplifier.simp_debug false);

        val (run_result, trace_chunks) =
          with_tracing_capture (fn () => run_method_on_state traced_state method local_facts);

        val trace_lines = normalize_trace_lines trace_chunks;
        val rewrite_names =
          trace_lines
          |> map_filter extract_rewrite_name
          |> dedup_preserve;

        val replay_rule_names =
          rewrite_names
          |> filter (fn name => can (Proof_Context.get_fact ctxt) (Facts.named name));

        (* Baseline bundles: include simp_thms so that the basic HOL simplifications
           simp does by default (True/False laws, boolean normalization, quantifier
           elimination) remain available without dragging in the full simpset. Without
           this, `simp only: R1 R2` leaves residuals like `True ∧ ¬False ∧ True` that
           a subsequent `done` can't close. `simp_thms` is small (~50 rules) relative
           to the default simpset (thousands), so the search-burden reduction is still
           massive. *)
        val baseline_rule_names =
          ["simp_thms"]
          |> filter (fn name => can (Proof_Context.get_fact ctxt) (Facts.named name));

        val full_rule_names = baseline_rule_names @ replay_rule_names;

        fun solved_by method_body =
          (case Exn.capture (fn () => #2 (run_method_on_state proof_state method_body local_facts)) () of
            Exn.Res solved => solved
          | Exn.Exn _ => false);

        val (method_solved, run_message) =
          (case run_result of
            Exn.Res (_, solved) => (solved, "")
          | Exn.Exn exn => (false, "Method execution failed: " ^ Runtime.exn_message exn));

        (* Goal thm of an open proof state; extracts the Pure-level proposition
           representing the current subgoals. *)
        fun state_goal_prop st = Thm.prop_of (#goal (Proof.goal st));

        (* Accepts a candidate *sequence* of method bodies iff running them in
           order from `proof_state` reaches an end-state equivalent to the
           original method's end-state. For the single-element case this is the
           same check as `same_state_as` (and replaces it); for multi-step
           plans it validates the whole chain before the Python side commits. *)
        fun sequence_same_state_as method_bodies =
          (case run_result of
            Exn.Res (st_orig, orig_solved) =>
              (case Exn.capture
                  (fn () => run_method_sequence proof_state method_bodies local_facts) () of
                Exn.Res (st_cand, cand_solved) =>
                  if orig_solved andalso cand_solved then true
                  else if orig_solved <> cand_solved then false
                  else
                    (case Exn.capture
                        (fn () => state_goal_prop st_cand aconv state_goal_prop st_orig) () of
                      Exn.Res b => b
                    | Exn.Exn _ => false)
              | Exn.Exn _ => false)
          | Exn.Exn _ => false);

        (* Candidate plans in preference order. Each plan is a list of method
           bodies to be issued as a sequence of `apply (…)` steps (with the
           last step rewritten to `by (…)` by the caller iff the original
           method closed the goal).

           Plan A — single-step `only:` form: the tightest emission, leaves the
             default simpset behind entirely. Works when simp_thms + traced
             rules are sufficient on their own.

           Plan B — two-stage split: first normalize with simp_thms alone, then
             apply the traced replay rules alone. Gives simp two much smaller
             simpsets per call. Works in more cases than Plan A because the
             intermediate states cleanly separate boolean normalization from
             domain-specific rewriting. *)
        val method_plans =
          if null replay_rule_names then []
          else
            (case method of
              "simp" =>
                [["simp only: " ^ space_implode " " full_rule_names]] @
                (if null baseline_rule_names then []
                 else
                   [["simp only: " ^ space_implode " " baseline_rule_names,
                     "simp only: " ^ space_implode " " replay_rule_names]])
            | "auto" =>
                [["auto simp only: " ^ space_implode " " full_rule_names]]
            | _ => []);

        fun first_valid_plan [] = NONE
          | first_valid_plan (plan :: rest) =
              if sequence_same_state_as plan then SOME plan
              else first_valid_plan rest;

        (* Mirror the original method's solved-ness when accepting the candidate, so
           the emitted last-step uses `by (…)` iff the original closed the goal. *)
        val (chosen_bodies, chosen_solved) =
          (case first_valid_plan method_plans of
            SOME plan => (plan, method_solved)
          | NONE => ([method], method_solved));

        val command = format_command_sequence using_prefix chosen_solved chosen_bodies;

        val trace_header =
          if null rewrite_names then "No rewrite rules were extracted from simp trace."
          else if null replay_rule_names then
            "Extracted rewrite rules (not directly replayable by name in this context): " ^
            space_implode " " rewrite_names
          else
            "Extracted rewrite rules: " ^ space_implode " " full_rule_names;

        val trace_log =
          cat_lines ([trace_header] @
            (if run_message = "" then [] else [run_message]) @
            trace_lines);
      in
        (chosen_solved, command, trace_log)
      end
  end;
(* ============================================================================
   Fact retrieval utilities
   ============================================================================ *)

(* Create fact references and token lists *)
fun get_refs_and_token_lists name = (Facts.named name, []);

(* Common parameters for fact retrieval *)
fun make_override adds dels =
  let
    val adds_refs_and_token_lists = map get_refs_and_token_lists adds;
    val dels_refs_and_token_lists = map get_refs_and_token_lists dels;
  in
    {add = adds_refs_and_token_lists, del = dels_refs_and_token_lists, only = false}
  end;

(* Get facts from proof state with common logic *)
fun get_facts_from_state state thy filter adds dels =
  let
    val ctxt = Proof.context_of state;
    val {facts = chained_thms, goal, ...} = Proof.goal state;
    val (_, hyp_ts, concl_t) = ATP_Util.strip_subgoal goal 1 ctxt;
    val override = make_override adds dels;
    val params = Sledgehammer_Commands.default_params thy [("fact_filter", filter)];
    val {induction_rules, max_facts, ...} = params;
    val all_facts = Sledgehammer_Fact.nearly_all_facts_of_context ctxt false override chained_thms hyp_ts concl_t;
    val max_num_facts = the_default 128 max_facts;
    val fact_lst = Sledgehammer_MaSh.relevant_facts ctxt params "" max_num_facts override hyp_ts concl_t all_facts;
    val induction_rules = the_default Sledgehammer_Prover.Exclude induction_rules;
    val factss = map (apsnd (Sledgehammer_Prover.maybe_filter_out_induction_rules induction_rules)) fact_lst;
  in
    (factss, ctxt)
  end;

(* ============================================================================
   Fact retrieval functions
   ============================================================================ *)

(* Retrieve facts from the proof state
   @param state - current proof state
   @param thy - theory
   @param filter - fact filter to use
   @param adds - facts to add
   @param dels - facts to delete
   @return formatted fact string
*)
fun retrieve_facts state thy filter adds dels = 
  let
    val (factss, _) = get_facts_from_state state thy filter adds dels;
  in
    Sledgehammer.string_of_factss factss
  end;

(* Format facts with theory names *)
fun string_of_facts_with_theory_names filter facts =
  string_of_int (length facts) ^ "<\\SEP>" ^ 
  (if filter = "" then "" else filter) ^ "<\\SEP>" ^ 
  (space_implode "<\\SEP>" 
    (map (fn x => String.concat [
      Thm.theory_long_name (#2 x), "<\\INNER_SEP>",
      (* New version should be as follows *)
      (* Thm_Name.print (Thm.derivation_name (#2 x)), "<\\INNER_SEP>", *)
      Thm.derivation_name (#2 x), "<\\INNER_SEP>", 
      #1 (#1 x)
    ]) facts));

(* Format fact lists with theory names *)
fun string_of_factss_with_theory_names factss =
  if forall (null o snd) factss then
    "Found no relevant facts"
  else
    cat_lines (map (fn (filter, facts) => string_of_facts_with_theory_names filter facts) factss);

(* Retrieve facts with theory names *)
fun retrieve_facts_with_theory_names state thy filter adds dels =
  let
    val (factss, _) = get_facts_from_state state thy filter adds dels;
  in
    string_of_factss_with_theory_names factss
  end;

(* ============================================================================
   MaSh learning
   ============================================================================ *)

(* Relearn MaSh with current proof state *)
fun mash_relearn state thy =
  let
    val ctxt = Proof.context_of state;
    val override = make_override [] [];
    val params = Sledgehammer_Commands.default_params thy
      [("timeout", string_of_real 2.0), ("slice", "false"), ("preplay_timeout", "0")];
    val _ = Sledgehammer_MaSh.mash_unlearn ctxt;
    val _ = Sledgehammer_MaSh.mash_learn ctxt params override (#facts (Proof.goal state)) false;
  in
    ()
  end;

end
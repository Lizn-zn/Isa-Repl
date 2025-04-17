(*  Title:      Auto_Isabelle.ML
    Modified by Zenan Li.

Try a combination of proof methods. Tweak from the try0.ML.
In Scala env, timeout mechanism is not working.
*)

structure Auto_Isabelle : sig
  val clean_theorem_text : string -> string
  type result = {name: string, command: string, time: int, state: Proof.state}
  val try_close : Time.time -> Proof.state ->  bool * string * string
  val retrive_facts : Proof.state -> theory -> string ->  string list -> string list -> string 
end = struct

fun clean_theorem_text (thm_text : string) = 
          XML.content_of (YXML.parse_body thm_text);

val num_goals = Thm.nprems_of o #goal o Proof.goal;

val named_methods =
  ["simp", "auto", "blast", "argo", "linarith", "presburger",
   "algebra", "fast", "fastforce", "force", "meson", "satx", "order"];

fun parse_method ctxt s =
  enclose "(" ")" s
  |> Token.explode (Thy_Header.get_keywords' ctxt) Position.start
  |> filter Token.is_proper
  |> Scan.read Token.stopper Method.parse
  |> (fn SOME (Method.Source src, _) => src | _ => raise Fail "expected Source")

fun simple_apply tac st =
  let
    val result = try (fn () => Seq.pull (tac st)) ()
  in
    case Option.join result of
        SOME (st', _) => (Time.zeroTime, st')
      | NONE => (Time.zeroTime, st)
  end

fun antiquot s = "\\" ^ "<open>" ^ s ^ "\\" ^ "<close>";

type result = {name: string, command: string, time: int, state: Proof.state}

fun apply_named_method name _ st =
    let
      val ctxt = Proof.context_of st;
      val local_facts = Facts.props (Proof_Context.facts_of ctxt) |> map #1 |> take 10; 
      val attrs =  "";
      val text =
        name ^ attrs
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
         if num_after = 0 then (
             SOME {name = name, command = command, time = Time.toMilliseconds time, state = st'}
          ) else (
            NONE
            )
   end 


fun try_close_async st =
  Future.fork (fn () =>
    let
      val apply_methods = map apply_named_method named_methods
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
    end)

(* fun try_close st = Future.join (try_close_async st) *)

fun try_close timeout st =
  let
    val future = try_close_async st
    val start_time = Time.now ()
    fun check_timeout () = Time.now () - start_time > timeout
    fun poll interval =
      if Future.is_finished future
      then Future.join future
      else if check_timeout ()
      then (Future.cancel future; (false, "All tactics timed out", ""))
      else (OS.Process.sleep (seconds interval); poll (Real.min ((interval * 2.0), 1.0)))
  in
    poll 0.1
  end

(* 
  retrive facts from the proof state
  filter: string, the filter to use
  adds: string list, the facts to add
  dels: string list, the facts to delete
*)

fun get_refs_and_token_lists (name) = (Facts.named name, []);

fun retrive_facts state thy filter adds dels  = 
  let
     val ctxt = Proof.context_of state;
     val {facts = chained_thms, goal, ...} = Proof.goal state;
     val (_, hyp_ts, concl_t) = ATP_Util.strip_subgoal goal 1 ctxt;
     val adds_refs_and_token_lists = map get_refs_and_token_lists adds;
     val dels_refs_and_token_lists = map get_refs_and_token_lists dels;
     val override = {add=adds_refs_and_token_lists,del=dels_refs_and_token_lists,only=false}
     val params = Sledgehammer_Commands.default_params thy
                  [("fact_filter", filter)];
     val {induction_rules, max_facts, ...} = params;
     val all_facts = (Sledgehammer_Fact.nearly_all_facts_of_context ctxt false override chained_thms hyp_ts) concl_t;
     val max_num_facts = (case max_facts of
                              SOME n => n
                            | NONE => 2048)
     val fact_lst = (Sledgehammer_MaSh.relevant_facts ctxt params "" max_num_facts override hyp_ts concl_t)
                            all_facts
     val induction_rules = the_default (Sledgehammer_Prover.Exclude) induction_rules
     val factss = map (apsnd (Sledgehammer_Prover.maybe_filter_out_induction_rules induction_rules)) fact_lst
     val fact_string = Sledgehammer.string_of_factss factss
  in 
     fact_string
  end

end

(*  Title:      Auto_Isabelle.ML
    Author:     Zenan Li

Try a combination of proof methods. Tweak from the try0.ML.
*)

structure Auto_Isabelle : sig
  val clean_theorem_text : string -> string
  type result = {name: string, command: string, time: int, state: Proof.state}
  val try_close : Proof.state ->  bool * string * string
end = struct

fun clean_theorem_text (thm_text : string) = 
          XML.content_of (YXML.parse_body thm_text);

val num_goals = Thm.nprems_of o #goal o Proof.goal
val timeout = (SOME (seconds 5.0))

val named_methods =
  ["simp", "auto", "blast", "metis", "argo", "linarith", "presburger",
   "algebra", "fast", "fastforce", "force", "meson", "satx", "order"]

fun parse_method ctxt s =
  enclose "(" ")" s
  |> Token.explode (Thy_Header.get_keywords' ctxt) Position.start
  |> filter Token.is_proper
  |> Scan.read Token.stopper Method.parse
  |> (fn SOME (Method.Source src, _) => src | _ => raise Fail "expected Source")

fun run_tac timeout_opt tac st =
  let val with_timeout =
    (case timeout_opt of SOME timeout => Timeout.apply_physical timeout | NONE => I)
  in with_timeout (Seq.pull o tac) st |> Option.map fst end

fun apply_recursive recurse elapsed0 timeout_opt apply st =
  (case Timing.timing (Option.join o try (run_tac timeout_opt apply)) st of
    ({elapsed, ...}, SOME st') =>
      if recurse andalso num_goals st' > 0 andalso num_goals st' < num_goals st then
        let val timeout_opt1 = (Option.map (fn timeout => timeout - elapsed) timeout_opt)
        in apply_recursive recurse (elapsed0 + elapsed) timeout_opt1 apply st' end
      else (elapsed0 + elapsed, st')
   |_ => (elapsed0, st))

fun antiquot s = "\\" ^ "<open>" ^ s ^ "\\" ^ "<close>";

type result = {name: string, command: string, time: int, state: Proof.state}

fun apply_named_method name timeout_opt st =
    let
      val ctxt = Proof.context_of st
      val local_facts = Facts.props (Proof_Context.facts_of ctxt) |> map #1 |> take 10; 
      val attrs =  ""
      val multiple_goals = false
      val text =
        name ^ attrs
        |> parse_method ctxt
        |> Method.method_cmd ctxt
        |> Method.Basic
        |> (fn m => Method.Combinator (Method.no_combinator_info, Method.Select_Goals 1, [m]));
      val apply =
        Proof.using [local_facts |> map (rpair [] o single)]
        #> Proof.refine text #> Seq.filter_results

      val (time, st') = apply_recursive multiple_goals Time.zeroTime timeout_opt apply st
      val num_after = num_goals st'
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


fun try_close st =
  let
      val apply_methods = map apply_named_method named_methods; 
      val results =
        map (fn method =>
          case method timeout st of
           SOME {command, name, time, ...} => SOME {command = command, name = name, time = time}
           | NONE => NONE 
        ) apply_methods
        |> List.mapPartial I 
  
        val (success, method, step) =
    case results of
      [] => (false, "All Failed", "")
    | results as {name, command, ...} :: _ =>
      let
        val method_times =
          results
          |> map (fn {name, time, ...} => (time, name))
          |> AList.coalesce (op =)
          |> map (swap o apsnd commas);
        val first_method_time = 
          case method_times of
             [] => ""
            | (_, t) :: _ => " (" ^ string_of_int t ^ " ms" ^ ")";
        val step =
          "Try this" ^ ": " ^
          Active.sendback_markup_command command ^
          first_method_time
      in
        (true, name, step)
      end 
   in
     (success, method, step)
end
   
end

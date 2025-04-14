(*  Title:      Auto_Isabelle.ML
    Modified by Zenan Li.

Try a combination of proof methods. Tweak from the try0.ML.
In Scala env, timeout mechanism is not working.
*)

structure Auto_Isabelle : sig
  val clean_theorem_text : string -> string
  type result = {name: string, command: string, time: int, state: Proof.state}
  val try_close : Proof.state ->  bool * string * string
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


fun try_close st =
  let
    val apply_methods = map apply_named_method named_methods; 
    val default_timeout = (SOME (seconds 0.0))
    val results =
        map (fn method =>
          case method default_timeout st of
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

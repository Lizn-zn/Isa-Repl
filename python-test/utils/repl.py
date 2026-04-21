# This file is used to wrap the Isa_REPL to start and end the REPL

import os
import re
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Tuple

from py4j.java_gateway import GatewayParameters, JavaGateway

import utils.config as config
from utils.isar_utils import (
    delete_comments,
    delete_texts,
    extract_theorem_names,
    replaced_by_sorry,
)
from utils.parser import parse_java_object, parse_tactic


class SafeIsaRepl:
    def __init__(self, real_isa):
        self._real_isa = real_isa

    def __getattr__(self, name):
        # original
        attr = getattr(self._real_isa, name)
        if callable(attr):
            def wrapper(*args, **kwargs):
                try:
                    return attr(*args, **kwargs)
                except Exception as e:
                    print(f"repl execution error: {e}")
                    return False, str(e) 
            return wrapper
        return attr

class IsaRepl:
    """
    IsaRepl wraps the Isa_REPL to start and end the REPL, and to initialize the REPL with a theory file.
    """
    def __init__(self, port=25333, create_port=True):
        self.port: int = port
        self.create_port: bool = create_port
        self.isa_repl: JavaGateway.JavaObject = None # type: ignore
        self.process: subprocess.Popen | None = None
        self._log_file = None
        return
    
    # support context manager
    def __enter__(self):
        """enter with block"""
        self.start()
        return self  # return self, for use in with block

    def _wait_for_completion(self, timeout):
        end_time = time.time() + timeout
        while getattr(self, '_pending_operations', 0) > 0:
            if time.time() > end_time:
                raise TimeoutError("waiting for operations to complete timeout")
            time.sleep(0.1)
            
    def __exit__(self, exc_type, exc_val, exc_tb):
        """exit with block"""
        if hasattr(self, '_pending_operations'):
            # wait for all pending operations to complete or timeout
            self._wait_for_completion(timeout=3000)  # set a reasonable timeout
        self.close()
        return False  # return False, to propagate exception

    def run_jar_file(self):
        if not os.path.exists("logs/isa_repl"):
            os.makedirs("logs/isa_repl", exist_ok=True)
        log_path = f"logs/isa_repl/isa_repl_port_{self.port}.log"
        self._log_file = open(log_path, "w")
        jar_path = config.ISA_REPL_PATH
        env = os.environ.copy()
        env["ISABELLE_HOME"] = os.path.expanduser("~/verification/isabelle/")

        process = subprocess.Popen([
            "java", "-jar", jar_path, str(self.port)
        ],
        stdout=self._log_file,
        stderr=self._log_file,
        env=env,
        )
        # Give the server some time to start
        time.sleep(1)
        if process.poll() is not None:
            self._log_file.flush()
            try:
                with open(log_path, "r", errors="replace") as f:
                    log_tail = f.read()[-500:].strip()
            except OSError:
                log_tail = ""
            raise RuntimeError(
                f"Failed to start IsaRepl on port {self.port}. {log_tail}"
            )
        self.process = process
        
    def start(self):
        if self.create_port:
            self.run_jar_file()
        gateway = JavaGateway(gateway_parameters=GatewayParameters(port=self.port, auto_convert=True))
        self.isa_repl = gateway.entry_point
        if self.isa_repl is None:
            raise ConnectionError("Failed to connect to Java Gateway")
        
    def close(self):
        if self.create_port and self.process is not None:
            if self.process.poll() is None:
                self.process.terminate()
                try:
                    self.process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.wait()
            self.process = None
        if self._log_file is not None:
            self._log_file.close()
            self._log_file = None
        return

    def initialize(
        self,
        theory_file: str,
        working_dir: str = ".tmp",
        session: str = "HOL",
        session_dirs: list[str] = [],
    ):
        self.theory_file = theory_file
        self.working_dir = working_dir
        self.session = session
        self.session_dirs = session_dirs
        res = self.isa_repl._initializeRepl(
            self.theory_file, self.working_dir, self.session, self.session_dirs
        )
        ok, msg = parse_java_object(res)
        if not ok:
            raise ValueError(f"Error when initializing the REPL. Get msg: {msg}")
        return ok, msg
    
    def reset(self):
        self.isa_repl._resetRepl(self.theory_file)

    def step(self, tactic):
        res = self.isa_repl._step(tactic)
        ok, msg = parse_java_object(res)
        return ok, msg
    
    def execute_steps(self, steps: list[str]):
        success, message = True, "No proof found"  
        for step in steps:
            success, message = self.step(step)
            if not success:
                print(message)
                break
        return success, message
    
    def step_to_target(self, path: str, target: str, exclude_list: list[str]=[]):
        with open(path, "r") as f:
            content = f.read()
        if target is None or target not in content:
            raise ValueError(f"cannot find target: {target}")
        if target != "":
            content = content.split(target, 1)[0] + "\n" + target
        ok, steps = self.parse(content)
        if not ok:
            raise ValueError(f"Error when parsing file {path}")
        steps = [step for step in steps if step.strip()]
        steps = delete_texts(delete_comments(steps))
        if path not in exclude_list:
            steps = replaced_by_sorry(steps)
        success, message = self.execute_steps(steps)
        if not success:
            raise ValueError(f"Error when executing the file {path}")
        return success, message
            
    def hammer(self):
        res = self.isa_repl._prove_by_hammer()
        return parse_java_object(res)
    
    def try_close(self):
        res = self.isa_repl._try_close()
        return parse_java_object(res)
    
    def check_by_nitpick(self):
        res = self.isa_repl._check_by_nitpick()
        return parse_java_object(res)
    
    def check_by_quickcheck(self):
        res = self.isa_repl._check_by_quickcheck()
        return parse_java_object(res)
    
    def auto_prove(self, max_steps: int=128):
        """ Adaptively apply a tactic to the current goal.
            It will always succeed, even if the tactic fails.
        
        Args:
            tactic (str): The tactic to apply.
            max_steps (int): The maximum number of steps to apply.

        Returns:
            str: The tactic applied.
            str: The message from the tactic.
        """
        steps = []
        for _ in range(max_steps):
            success, msg = self.try_close()
            if success:
                tactic = parse_tactic(msg.split("<\\SEP>")[0])
                ok, msg = self.step(tactic)
                if ok:
                    steps.append(tactic)
                    if self.proof_finished() and msg == "":
                        return steps, msg
                    else:
                        # print(f"Tactic `try_close` get a tactic \n`{tactic}`,\n the remaining goal is \n`{msg}`")
                        pass
                else:
                    print(f"Tactic `try_close` get a tactic \n`{tactic}`,\n but failed to close the goal, get message \n`{msg}`")
                    break
            else:
                self.relearn_isar() # NOTE: this is a hack to avoid the retrieval cheating
                success, msg = self.hammer()
                if success:
                    tactic = parse_tactic(msg.split("<\\SEP>")[0])
                    ok, msg = self.step(tactic)
                    if ok:
                        steps.append(tactic)
                        if self.proof_finished() and msg == "":
                            return steps, msg
                        else:
                            # print(f"Tactic `hammer` get a tactic \n`{tactic}`,\n the remaining goal is \n`{msg}`")
                            pass
                    else:
                        print(f"Tactic `hammer` get a tactic \n`{tactic}`,\n but failed to close the goal, get message \n`{msg}`")
                        break
                else:
                    # print(f"Tactic `hammer` get a tactic \n`{tactic}`,\n but failed to close the goal, get message \n`{msg}`")
                    break
        return [], msg

    def relearn_isar(self):
        """Same as sledgehammer relearn_isar command.
        
        Returns:
            Tuple[bool, str]: The result of the relearn operation..
        """
        res = self.isa_repl._mash_state_relearn()
        ok, msg = parse_java_object(res)
        return ok, msg

    def parse(self, thy:str) -> Tuple[bool, List[str]]:
        """Parse a theorem to steps.

        Args:
            thy (str): The theorem to parse.

        Returns:
            str: The steps of the theorem in the format of "<\\SEP>".
        """
        res = self.isa_repl._parse_to_steps(thy)
        ok, msg = parse_java_object(res)
        if ok:
            return ok, msg.split("<\\SEP>")
        else:
            return ok, []
    
    def translate_to_smt(self):
        res = self.isa_repl._translate_to_smt()
        return parse_java_object(res)
        
    def extract_vars(self) -> Tuple[bool, List[str]]:
        """Extract the variables from the current proof state.
        """
        res = self.isa_repl._extract_vars()
        ok, res = parse_java_object(res)
        var_lst = res.split("<\\SEP>")
        return ok, var_lst
    
    def extract_assms(self) -> Tuple[bool, Dict[str, str]]:
        """Extract the assumptions from the current proof state.
        """
        res = self.isa_repl._extract_assms()
        ok, res = parse_java_object(res)
        assms = res.split("<\\SEP>")
        return ok, assms
        
    def extract_goal(self) -> Tuple[bool, str]:
        """Extract the goal from the current proof state.
        """
        res = self.isa_repl._extract_goal()
        ok, res = parse_java_object(res)
        return ok, res
    
    def proof_finished(self) -> Tuple[bool, str]:
        """Check if the proof is finished.
        """
        res = self.isa_repl._proof_finished()
        ok, res = parse_java_object(res)
        return ok, res
    
    def extract_theorem(self) -> Tuple[bool, List[str], Dict[str, str], str]:
        """Extract the theorem from the current proof state.
        """
        ok1, vars = self.extract_vars()
        ok2, assms = self.extract_assms()
        ok3, goal = self.extract_goal()
        if not (ok1 and ok2 and ok3):
            return False, [], [], ""
        else:
            return True, vars, assms, goal
        
    def clone_tls(self, tls_name: str) -> Tuple[bool, str]:
        """Clone the current top level state.

        Args:
            tls_name (str): The name of the top level state to clone.

        Returns:
            Tuple[bool, str]: The result of the clone operation.
        """
        res = self.isa_repl._clone_tls(tls_name)
        return parse_java_object(res)
    
    def focus_tls(self, tls_name: str) -> Tuple[bool, str]:
        """Focus on a top level state.

        Args:
            tls_name (str): The name of the top level state to focus on.

        Returns:
            Tuple[bool, str]: The result of the focus operation.
        """
        res = self.isa_repl._focus_tls(tls_name)
        return parse_java_object(res)
    
    def remove_tls(self, tls_name: str) -> Tuple[bool, str]:
        """Remove the top level state.
        """
        res = self.isa_repl._remove_tls(tls_name)
        return parse_java_object(res)
    
    def find_theorems(self, patterns: list[str]) -> Tuple[bool, str]:
        return parse_java_object(self.isa_repl._find_theorems(patterns))

    def compile(self, isar_proof: str | None = None) -> Tuple[bool, str]:
        if isar_proof is None:
            res = self.isa_repl._compile()
        else:
            res = self.isa_repl._compile(isar_proof)
        return parse_java_object(res)

    @staticmethod
    def _parse_explicitize_output(raw_result: str) -> Tuple[bool, bool, list[str], str]:
        parts = raw_result.split("<\\SEP>", 3)
        if not parts:
            return False, False, [], "Malformed explicitize result: empty response"
        if parts[0] == "False":
            message = parts[1] if len(parts) > 1 else "Unknown explicitize_proof error"
            return False, False, [], message
        if len(parts) != 4:
            return False, False, [], f"Malformed explicitize result: {raw_result}"
        solved = parts[1].strip().lower() == "true"
        # The ML side joins a plan (ordered list of proof commands) with
        # <\CMD_SEP>. A single-command plan has no separator and becomes a
        # one-element list.
        commands = [piece for piece in parts[2].split("<\\CMD_SEP>") if piece]
        return True, solved, commands, parts[3]

    @staticmethod
    def _extract_rewrite_name(trace_line: str) -> str | None:
        prefix = "Applying instance of rewrite rule "
        if not trace_line.startswith(prefix):
            return None
        rest = trace_line[len(prefix) :].strip()
        if rest.startswith('"'):
            chunks = rest.split('"')
            if len(chunks) >= 2 and chunks[1].strip():
                return chunks[1].strip()
        if ":" in rest:
            name = rest.split(":", 1)[0].strip()
        else:
            name = rest.strip()
        return name or None

    @staticmethod
    def _extract_rules_from_header(header: str) -> list[str]:
        if not header.startswith("Extracted rewrite rules"):
            return []
        if ":" not in header:
            return []
        payload = header.split(":", 1)[1].strip()
        if not payload:
            return []
        return [token for token in payload.split() if token]

    def _structure_trace_log(self, trace_log: str) -> dict[str, Any]:
        lines = [line for line in trace_log.splitlines() if line.strip()]
        summary = lines[0] if lines else ""
        rewrite_rules: list[str] = []
        for line in lines:
            rule_name = self._extract_rewrite_name(line)
            if rule_name and rule_name not in rewrite_rules:
                rewrite_rules.append(rule_name)
        if not rewrite_rules:
            for rule_name in self._extract_rules_from_header(summary):
                if rule_name not in rewrite_rules:
                    rewrite_rules.append(rule_name)
        return {
            "summary": summary,
            "rewrite_rules": rewrite_rules,
            "trace_lines": lines,
            "raw": trace_log,
        }

    @staticmethod
    def _is_informative_explicit(explicit: str) -> bool:
        normalized = " ".join(explicit.split())
        return re.fullmatch(r"(?:apply|by)\s*\(\s*(?:simp|auto)\s*\)", normalized) is None

    @staticmethod
    def _infer_traceable_method(proof_step: str) -> str | None:
        pattern = re.compile(
            r"^\s*(?:by|apply)\s*(?:\(\s*)?(simp|auto)(?:\s*\))?\s*$",
            re.IGNORECASE,
        )
        match = pattern.match(proof_step.strip())
        if not match:
            return None
        return match.group(1).lower()

    @staticmethod
    def _normalize_statement_name(raw_step: str, step_index: int) -> str:
        theorem_name = extract_theorem_names(raw_step)
        if theorem_name:
            return theorem_name
        head_match = re.match(r"^\s*(lemma|theorem|corollary)\b", raw_step)
        if head_match:
            return f"<unnamed_{head_match.group(1)}_step_{step_index}>"
        return ""

    @staticmethod
    def _shorten_step_for_log(step: str) -> str:
        one_line = " ".join(step.strip().split())
        if len(one_line) <= 200:
            return one_line
        return one_line[:197] + "..."

    @staticmethod
    def _make_default_output_path(source_thy_path: str) -> str:
        src = Path(source_thy_path)
        if src.suffix != ".thy":
            return str(src.with_name(src.name + "_explicit"))
        return str(src.with_name(f"{src.stem}_explicit{src.suffix}"))

    @staticmethod
    def _make_default_log_path(output_thy_path: str) -> str:
        output = Path(output_thy_path)
        return str(output.with_suffix(output.suffix + ".explicitize.log"))

    def explicitize_proof(
        self,
        method: str = "simp",
        timeout_in_millis: int = 65000,
    ) -> Tuple[bool, dict[str, Any]]:
        raw_result = self.isa_repl._explicitize_proof(method, int(timeout_in_millis))
        ok, solved, commands, trace_or_error = self._parse_explicitize_output(raw_result)
        if not ok:
            return False, {
                "method": method,
                "solved": False,
                "explicit_commands": [],
                "explicit_command": "",
                "trace": self._structure_trace_log(""),
                "error": trace_or_error,
            }
        return True, {
            "method": method,
            "solved": solved,
            "explicit_commands": commands,
            # Kept for back-compat with callers that only want a single string.
            "explicit_command": commands[0] if commands else "",
            "trace": self._structure_trace_log(trace_or_error),
            "error": "",
        }

    def trace_proof_step(
        self,
        proof_step: str,
        timeout_in_millis: int = 65000,
    ) -> Tuple[bool, dict[str, Any]]:
        method = self._infer_traceable_method(proof_step)
        if method is None:
            return False, {
                "input_step": proof_step,
                "method": None,
                "solved": False,
                "explicit_command": "",
                "trace": self._structure_trace_log(""),
                "error": (
                    "Unsupported proof step for tracing. "
                    "Currently supported: exact `by/apply simp` and `by/apply auto` forms."
                ),
            }

        ok, payload = self.explicitize_proof(method, timeout_in_millis=timeout_in_millis)
        payload["input_step"] = proof_step
        return ok, payload

    def explicitize_theory_file(
        self,
        source_thy_path: str,
        output_thy_path: str | None = None,
        log_path: str | None = None,
        timeout_in_millis: int = 65000,
        target_methods: tuple[str, ...] = ("simp", "auto"),
        working_dir: str | None = None,
        session: str | None = None,
        session_dirs: list[str] | None = None,
        check_output_with_compile: bool = True,
        show_progress: bool = False,
    ) -> dict[str, Any]:
        source_abs = os.path.abspath(source_thy_path)
        if output_thy_path is None:
            output_thy_path = self._make_default_output_path(source_abs)
        output_abs = os.path.abspath(output_thy_path)
        if output_abs == source_abs:
            raise ValueError("output_thy_path must be different from source_thy_path")
        if log_path is None:
            log_path = self._make_default_log_path(output_abs)
        log_abs = os.path.abspath(log_path)

        resolved_working_dir = working_dir if working_dir is not None else getattr(self, "working_dir", ".tmp")
        resolved_session = session if session is not None else getattr(self, "session", "HOL")
        resolved_session_dirs = (
            session_dirs if session_dirs is not None else list(getattr(self, "session_dirs", []))
        )

        with open(source_abs, "r", encoding="utf-8") as source_file:
            source_content = source_file.read()

        self.initialize(source_abs, resolved_working_dir, resolved_session, resolved_session_dirs)
        ok, parsed_steps = self.parse(source_content)
        if not ok:
            raise ValueError(f"Failed to parse source theory: {source_abs}")

        output_steps: list[str] = []
        replacement_logs: list[dict[str, Any]] = []
        active_statement = ""
        target_method_set = {method.lower() for method in target_methods}
        total_steps = len(parsed_steps)
        replaced_count = 0
        skipped_count = 0

        def _print_progress() -> None:
            if not show_progress:
                return
            sys.stderr.write(
                f"\r[explicitize] step {step_index + 1}/{total_steps} "
                f"(replaced={replaced_count}, skipped={skipped_count})"
            )
            sys.stderr.flush()

        for step_index, raw_step in enumerate(parsed_steps):
            _print_progress()
            if not raw_step.strip():
                continue

            statement_name = self._normalize_statement_name(raw_step, step_index)
            if statement_name:
                active_statement = statement_name

            maybe_method = self._infer_traceable_method(raw_step)
            if maybe_method and maybe_method in target_method_set:
                trace_ok, trace_payload = self.trace_proof_step(raw_step, timeout_in_millis=timeout_in_millis)
                explicit_commands = [
                    cmd.strip()
                    for cmd in trace_payload.get("explicit_commands", [])
                    if cmd.strip()
                ]
                rewrite_rules = trace_payload.get("trace", {}).get("rewrite_rules", [])
                uses_apply = raw_step.lstrip().startswith("apply")
                if uses_apply and explicit_commands:
                    # Tracer may emit `by (...)` on the last step when the method closes
                    # the proof, but the original was `apply` (expecting a following
                    # `done`). Rewrite the last step's `by` → `apply` so the proof stays
                    # valid.
                    explicit_commands[-1] = re.sub(
                        r"\bby\s+", "apply ", explicit_commands[-1], count=1
                    )

                # Candidate plans. Each plan is an ordered list of proof commands.
                # The tracer has already validated that the sequence reaches the
                # same end-state as the original method, so we trust it, but we
                # still guard against step-level failures (e.g., syntactic issues
                # the ML check can't catch) by rolling back to the next plan.
                candidate_plans: list[list[str]] = []
                if (
                    trace_ok
                    and explicit_commands
                    and any(self._is_informative_explicit(cmd) for cmd in explicit_commands)
                ):
                    candidate_plans.append(list(explicit_commands))
                if rewrite_rules:
                    # Looser fallback: keep the default simpset and just surface the
                    # traced rules via `add:`. Preserves simprocs/congs that `only:` drops,
                    # so it closes whatever plain simp/auto closes while still documenting
                    # the rule dependencies.
                    verb = "apply" if uses_apply else "by"
                    rule_list = " ".join(rewrite_rules)
                    candidate_plans.append([f"{verb} ({maybe_method} add: {rule_list})"])

                chosen_plan: list[str] | None = None
                # Checkpoint the current proof state via the TLS store so we
                # can roll back between candidate plans. `_explicitize_checkpoint`
                # is a local, step-scoped name; we always clean it up before
                # moving on.
                checkpoint = f"_explicitize_checkpoint_{step_index}"
                self.clone_tls(checkpoint)
                try:
                    for plan in candidate_plans:
                        applied_count = 0
                        ok_all = True
                        for step_cmd in plan:
                            apply_ok, _ = self.step(step_cmd)
                            if not apply_ok:
                                ok_all = False
                                break
                            applied_count += 1
                        if ok_all:
                            chosen_plan = plan
                            break
                        # Plan failed mid-way. Restore the checkpoint so the
                        # next candidate starts from the pre-plan state.
                        if applied_count > 0:
                            self.focus_tls(checkpoint)
                finally:
                    self.remove_tls(checkpoint)

                if chosen_plan is not None:
                    output_steps.extend(chosen_plan)
                    replaced_count += 1
                    replacement_logs.append(
                        {
                            "status": "replaced",
                            "step_index": step_index,
                            "statement": active_statement or "<unknown>",
                            "method": maybe_method,
                            "original_step": self._shorten_step_for_log(raw_step),
                            "replacement_step": self._shorten_step_for_log(
                                " ; ".join(chosen_plan)
                            ),
                            "replacement_steps": [
                                self._shorten_step_for_log(c) for c in chosen_plan
                            ],
                            "rewrite_rules": rewrite_rules,
                        }
                    )
                    continue

                fallback_ok, fallback_msg = self.step(raw_step)
                if not fallback_ok:
                    raise ValueError(
                        f"Step failed while rewriting source theory at step {step_index}. "
                        f"Original step also failed: {fallback_msg}"
                    )
                output_steps.append(raw_step)
                skipped_count += 1
                skip_reason = trace_payload.get("error") or (
                    "No informative explicit form available"
                    if not candidate_plans
                    else "All explicit candidate plans failed to apply"
                )
                replacement_logs.append(
                    {
                        "status": "skipped",
                        "step_index": step_index,
                        "statement": active_statement or "<unknown>",
                        "method": maybe_method,
                        "original_step": self._shorten_step_for_log(raw_step),
                        "replacement_step": "",
                        "replacement_steps": [],
                        "rewrite_rules": rewrite_rules,
                        "reason": skip_reason,
                    }
                )
                continue

            ok_step, msg_step = self.step(raw_step)
            if not ok_step:
                raise ValueError(
                    f"Failed to execute source theory step {step_index}: "
                    f"{self._shorten_step_for_log(raw_step)}. Isabelle message: {msg_step}"
                )
            output_steps.append(raw_step)

        if show_progress:
            sys.stderr.write("\n")
            sys.stderr.flush()

        rewritten_content = "\n".join(output_steps) + "\n"

        Path(output_abs).parent.mkdir(parents=True, exist_ok=True)
        with open(output_abs, "w", encoding="utf-8") as output_file:
            output_file.write(rewritten_content)

        compile_ok = True
        compile_msg = ""
        if check_output_with_compile:
            self.initialize(output_abs, resolved_working_dir, resolved_session, resolved_session_dirs)
            compile_ok, compile_msg = self.compile()
            if not compile_ok:
                raise ValueError(
                    f"Generated explicitized theory failed to compile: {compile_msg}. "
                    f"Output file: {output_abs}"
                )

        Path(log_abs).parent.mkdir(parents=True, exist_ok=True)
        with open(log_abs, "w", encoding="utf-8") as log_file:
            log_file.write(f"source_theory: {source_abs}\n")
            log_file.write(f"output_theory: {output_abs}\n")
            log_file.write(f"replaced_steps: {sum(1 for item in replacement_logs if item['status'] == 'replaced')}\n")
            log_file.write(f"skipped_candidates: {sum(1 for item in replacement_logs if item['status'] == 'skipped')}\n")
            if check_output_with_compile:
                log_file.write(f"compile_check: {compile_ok}\n")
                log_file.write(f"compile_message: {compile_msg}\n")
            log_file.write("\n")
            for entry in replacement_logs:
                log_file.write(
                    f"[{entry['status'].upper()}] step={entry['step_index']} "
                    f"statement={entry['statement']} method={entry['method']}\n"
                )
                log_file.write(f"original: {entry['original_step']}\n")
                if entry.get("replacement_step"):
                    log_file.write(f"replacement: {entry['replacement_step']}\n")
                if entry.get("rewrite_rules"):
                    log_file.write("rewrite_rules: " + " ".join(entry["rewrite_rules"]) + "\n")
                if entry.get("reason"):
                    log_file.write(f"reason: {entry['reason']}\n")
                log_file.write("\n")

        return {
            "source_theory": source_abs,
            "output_theory": output_abs,
            "log_file": log_abs,
            "replacements": replacement_logs,
            "compile_checked": check_output_with_compile,
            "compile_ok": compile_ok,
            "compile_message": compile_msg,
        }

import os

from lib import (
    L4V_PATH,
    PORT,
    delete_comments,
    get_repl,
    needs_l4v,
    replaced_by_sorry,
    split_result,
)


@needs_l4v
def test_parse_l4v_theory():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath(
        os.path.join(L4V_PATH, "proof/refine/ARM/CSpace_R.thy")
    )

    ok, msg = split_result(
        isa_repl._initializeRepl(theory_file, L4V_PATH, "Refine", [L4V_PATH])
    )
    assert ok, msg

    with open(theory_file, "r", encoding="utf-8") as f:
        content = f.read()

    steps = isa_repl._parse_to_steps(content).split("<\\SEP>")
    steps = delete_comments(steps)
    steps = replaced_by_sorry(steps)

    plain = False
    if plain:
        for i, step in enumerate(steps):
            print(i, step, "\n", isa_repl._step(steps[i]))
    else:
        i, unprocessed = 1, ""
        while i < len(steps):
            result = isa_repl._step(unprocessed + steps[i])
            if "False<\\SEP>" in result:
                unprocessed += steps[i] + "\n"
            else:
                unprocessed = ""
            i += 1


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_parse_l4v_theory()
        print("test_parse_l4v_theory passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()

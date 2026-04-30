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
def test_hammer_facts_with_thy():
    isa_repl = get_repl(PORT)
    theory_file = os.path.abspath(
        os.path.join(L4V_PATH, "proof/invariant-abstract/Deterministic_AI.thy")
    )

    ok, msg = split_result(
        isa_repl._initializeRepl(theory_file, L4V_PATH, "AInvs", [L4V_PATH])
    )
    assert ok, msg

    with open(theory_file, "r", encoding="utf-8") as f:
        content = f.read()

    steps = isa_repl._parse_to_steps(content).split("<\\SEP>")
    target = ""
    for step in steps:
        if "lemma no_children_empty_desc" in step:
            target = step
            break

    steps = delete_comments(steps)
    steps = replaced_by_sorry(steps)

    i, unprocessed = 1, ""
    while i < len(steps):
        result = isa_repl._step(unprocessed + steps[i])
        if target and target in unprocessed + steps[i]:
            print(isa_repl._mash_state_relearn())
            result = isa_repl._extract_hammer_facts_with_thy_names("mesh")
            result_lst = result.split("<\\SEP>")
            print(result_lst[:10])
            break
        if "False<\\SEP>" in result:
            unprocessed += steps[i] + "\n"
        else:
            unprocessed = ""
        i += 1

    isa_repl._exit()


if __name__ == "__main__":
    from lib import run_jar_file
    jvm_process = run_jar_file("target/IsaREPL.jar")
    try:
        test_hammer_facts_with_thy()
        print("test_hammer_facts_with_thy passed")
    finally:
        jvm_process.terminate()
        jvm_process.wait()

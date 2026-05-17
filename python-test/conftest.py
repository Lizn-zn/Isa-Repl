import os

import pytest
from lib import PORT, run_jar_file


def pytest_configure(config):
    config.addinivalue_line("markers", "l4v: tests requiring seL4 L4V_PATH")


@pytest.fixture(scope="session", autouse=True)
def _jar_process():
    _repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    jar_path = os.path.join(_repo, "target", "IsaREPL.jar")
    jvm_process = run_jar_file(jar_path, PORT)
    yield jvm_process
    jvm_process.terminate()
    jvm_process.wait()

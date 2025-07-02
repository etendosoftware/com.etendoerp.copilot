import json

from evaluation.utils import prepare_report_data


def is_close(a, b):
    low = b - 0.01
    high = b + 0.01
    return low < a < high


# Helper classes to simulate objects with dot notation (attributes)
class FakeEvalResult:
    def __init__(self, score=None, comment=None):
        self.score = score
        self.comment = comment


class FakeRun:
    def __init__(self, inputs=None, outputs=None, child_runs=None):
        self.inputs = inputs or {}
        self.outputs = outputs or {}
        self.child_runs = child_runs or []


class FakeResultItem(dict):
    def __init__(self, evaluation_results=None, run=None):
        super().__init__()
        self["evaluation_results"] = evaluation_results
        self["run"] = run


class FakeResultsObj:
    def __init__(self, results):
        self._results = results


def test_invalid_empty_results_obj():
    # No _results
    class Empty:
        pass

    out = prepare_report_data(Empty())
    assert out["error"]
    assert out["table_items"] == []
    assert out["avg_score"] is None

    # _results not a list
    class WrongResults:
        _results = 42

    out2 = prepare_report_data(WrongResults())
    assert out2["error"]

    # _results is empty
    out3 = prepare_report_data(FakeResultsObj([]))
    assert out3["error"]


def test_basic_valid_result():
    # 1 result, with evaluation_results and run with messages
    eval_res = FakeEvalResult(score=0.9, comment="Looks good!")
    user_msg = {"kwargs": {"type": "human", "content": "User prompt here"}}
    gen_msg = {"message": {"kwargs": {"content": "AI answer"}}}
    run = FakeRun(inputs={"messages": [user_msg]}, outputs={"generations": [gen_msg]})
    result_item = {"evaluation_results": {"results": [eval_res]}, "run": run}
    fake_obj = FakeResultsObj([result_item])
    out = prepare_report_data(fake_obj)
    assert out["error"] is None
    assert is_close(out["avg_score"], 0.9)  # Allow small floating point error
    assert len(out["table_items"]) == 1
    ti = out["table_items"][0]
    assert ti["comment"] == "User prompt here"
    assert is_close(ti["score"], 0.9)  # Allow small floating point error
    assert ti["output"] == "AI answer"
    assert ti["eval_comment"] == "Looks good!"


def test_eval_results_not_a_list():
    # Single dict for results
    eval_res = FakeEvalResult(score=0.7, comment="Fine")
    run = FakeRun()
    result_item = {"evaluation_results": {"results": eval_res}, "run": run}  # not a list!
    out = prepare_report_data(FakeResultsObj([result_item]))
    assert is_close(out["avg_score"], 0.7)
    assert is_close(out["table_items"][0]["score"], 0.7)


def test_no_child_runs_main_run():
    # No child_runs, run still exists
    eval_res = FakeEvalResult(score=0.5, comment="Half")
    run = FakeRun(inputs={"data": 1}, outputs={"generations": []})
    result_item = {"evaluation_results": {"results": [eval_res]}, "run": run}
    out = prepare_report_data(FakeResultsObj([result_item]))
    assert is_close(out["avg_score"], 0.5)
    assert json.loads(out["table_items"][0]["comment"]) == {"data": 1}


def test_child_runs_multiple_items():
    # 2 child runs, 2 eval_results, different messages for both
    eval_res1 = FakeEvalResult(score=1.0, comment="A")
    eval_res2 = FakeEvalResult(score=0.0, comment="B")
    user_msg1 = {"kwargs": {"type": "human", "content": "Prompt 1"}}
    user_msg2 = {"kwargs": {"type": "human", "content": "Prompt 2"}}
    gen_msg1 = {"message": {"kwargs": {"content": "Out1"}}}
    gen_msg2 = {"message": {"kwargs": {"content": "Out2"}}}
    child_run1 = FakeRun(inputs={"messages": [user_msg1]}, outputs={"generations": [gen_msg1]})
    child_run2 = FakeRun(inputs={"messages": [user_msg2]}, outputs={"generations": [gen_msg2]})
    run = FakeRun(child_runs=[child_run1, child_run2])
    result_item = {"evaluation_results": {"results": [eval_res1, eval_res2]}, "run": run}
    out = prepare_report_data(FakeResultsObj([result_item]))
    scores = sorted([ti["score"] for ti in out["table_items"]])
    assert scores == [0.0, 1.0]
    assert 0.49 < out["avg_score"] < 0.51
    texts = [ti["comment"] for ti in out["table_items"]]
    assert "Prompt 1" in texts and "Prompt 2" in texts


def test_eval_result_with_score_minus_1_excluded_from_avg():
    eval_res1 = FakeEvalResult(score=-1, comment="No score")
    eval_res2 = FakeEvalResult(score=0.4, comment="OK")
    run = FakeRun()
    result_item = {"evaluation_results": {"results": [eval_res1, eval_res2]}, "run": run}
    out = prepare_report_data(FakeResultsObj([result_item]))
    # Only 0.4 is considered for average
    assert is_close(out["avg_score"], 0.4)
    assert [ti["score"] for ti in out["table_items"]].count(-1) == 1
    assert [ti["score"] for ti in out["table_items"]].count(0.4) == 1


def test_error_handling_in_result_item(monkeypatch):
    # Simulate an attribute error when accessing .score
    class BrokenEvalResult:
        @property
        def score(self):
            raise ValueError("broken!")

        comment = "bad"

    run = FakeRun()
    result_item = {"evaluation_results": {"results": [BrokenEvalResult()]}, "run": run}
    out = prepare_report_data(FakeResultsObj([result_item]))
    assert out["error"]
    assert out["table_items"][0]["output"] == "Error"

import os
from langsmith import unit 

@unit
def test_check_file_is_created(set_fake_openai_api_key):
    from copilot.core.local_history import local_history_recorder

    assert os.path.isfile(local_history_recorder._history_filepath)
    os.remove(local_history_recorder._history_filepath)

@unit
def test_record_get_records(set_fake_openai_api_key):
    from copilot.core.local_history import LocalHistoryRecorder

    local_history_recorder = LocalHistoryRecorder()
    local_history_recorder.record_chat(chat_question="Fake question 1", chat_answer="Fake Answer 1")
    local_history_recorder.record_chat(chat_question="Fake question 2", chat_answer="Fake Answer 2")
    local_history_recorder.record_chat(chat_question="Fake question 3", chat_answer="Fake Answer 3")

    records = local_history_recorder.get_chat_history()
    assert len(records) == 3
    assert records[0].question == "Fake question 1"
    assert records[0].answer == "Fake Answer 1"

    assert records[1].question == "Fake question 2"
    assert records[1].answer == "Fake Answer 2"

    assert records[2].question == "Fake question 3"
    assert records[2].answer == "Fake Answer 3"
    os.remove(local_history_recorder._history_filepath)

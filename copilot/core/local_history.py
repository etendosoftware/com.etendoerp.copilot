import csv

from dataclasses import dataclass
from datetime import datetime
from typing import Final, TypeAlias


LOCAL_HISTORY_FILEPATH: Final[str] = "/tmp/chat_history.csv"
CSV_HEADERS: list[str] = ["datetime", "question", "answer"]
HISTORY_DATETIME_FORMAT: Final[str] = "%m/%d/%yT%H:%M:%S"

@dataclass
class HistoryRecord:
    datetime: str
    question: str
    answer: str

ChatHistory: TypeAlias = list[HistoryRecord]


class LocalHistoryRecorder:

    def __init__(self, history_filepath: str = LOCAL_HISTORY_FILEPATH):
        self._history_filepath = history_filepath
        with open(self._history_filepath, 'w', newline='') as csv_file:
            writer = csv.DictWriter(csv_file, fieldnames=CSV_HEADERS)
            writer.writeheader()

    def record_chat(self, chat_question: str, chat_answer: str):
        current_time = datetime.now().strftime(HISTORY_DATETIME_FORMAT)
        with open(self._history_filepath, 'a', newline='') as csv_file:
            writer = csv.DictWriter(csv_file, fieldnames=CSV_HEADERS)
            writer.writerow(
                {"datetime": current_time, "question": chat_question, "answer": chat_answer}
            )

    def get_chat_history(self) -> ChatHistory:
        history_records: ChatHistory = []
        with open(self._history_filepath, 'r') as csv_file:
            csv_reader = csv.DictReader(csv_file)
            for row in csv_reader:
                history_records.append(HistoryRecord(**row))

        return history_records


local_history_recorder = LocalHistoryRecorder()

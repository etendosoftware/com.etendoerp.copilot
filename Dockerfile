# Use the official Python 3.10 image as the base
FROM python:3.10

# Set the working directory
WORKDIR /app

ENV USE_CUDA=0
ENV TRANSFORMERS_OFFLINE=1

COPY pyproject.toml /app/pyproject.toml
COPY ./copilot /app/copilot
COPY ./run.py /app/run.py

RUN pip install poetry \
  && poetry config virtualenvs.create false \
  && poetry install --no-interaction --no-ansi --without dev

CMD ["python", "run.py"]

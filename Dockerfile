FROM python:3.10-slim-buster

WORKDIR /app

ENV USE_CUDA=0

COPY pyproject.toml /app/pyproject.toml
COPY ./copilot /app/copilot
COPY ./run.py /app/run.py
COPY ./tools_config.json /app/tools_config.json
COPY README.md /app/README.md

RUN pip install poetry==1.5.1 \
  && poetry config virtualenvs.create false \
  && poetry install --no-interaction --no-ansi --without dev

CMD poetry run python run.py

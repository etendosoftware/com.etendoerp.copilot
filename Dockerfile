FROM python:3.10

# Set the working directory
WORKDIR /app

ENV USE_CUDA=0

COPY pyproject.toml /app/pyproject.toml
COPY ./copilot /app/copilot
COPY ./run.py /app/run.py
COPY ./tools_config.json /app/tools_config.json
COPY README.md /app/README.md

RUN pip install poetry \
  && poetry config virtualenvs.create false \
  && poetry install --no-interaction --no-ansi --without dev

CMD ["python", "run.py"]

FROM python:3.10-slim-buster AS requirements-stage
WORKDIR /tmp
RUN pip install poetry && poetry self add poetry-plugin-export
COPY ./pyproject.toml ./poetry.lock* /tmp/
RUN poetry export -f requirements.txt --output requirements.txt --without-hashes
# Second stage, copy over the requirements and install them
FROM python:3.10
RUN apt update && apt install -y libzbar0 curl && \
    curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && \
    apt install -y nodejs && \
    npm install -g npm@latest
WORKDIR /app
COPY --from=requirements-stage /tmp/requirements.txt /app/requirements.txt
CMD ["pip", "install", "-r", "/app/requirements.txt", "&&", "python", "run.py"]
COPY ./copilot /app/copilot
COPY ./tools /app/tools
COPY ./run.py /app/run.py
COPY ./tools_config.json /app/tools_config.json
COPY README.md /app/README.md
CMD ["python", "run.py"]

FROM python:3.12.9-slim AS requirements-stage
WORKDIR /tmp
RUN pip install poetry && poetry self add poetry-plugin-export
COPY ./pyproject.toml ./poetry.lock* /tmp/
RUN poetry export -f requirements.txt --output requirements.txt --without-hashes

FROM python:3.12.9
RUN apt update && apt install -y libzbar0 curl
RUN curl -Ls https://astral.sh/uv/install.sh | sh
ENV PATH="/root/.cargo/bin:$PATH"
WORKDIR /app
COPY --from=requirements-stage /tmp/requirements.txt /app/requirements.txt
COPY ./copilot /app/copilot
COPY ./tools /app/tools
COPY ./run.py /app/run.py
COPY ./tools_config.json /app/tools_config.json
COPY README.md /app/README.md
CMD ["sh", "-c", "uv pip install -r /app/requirements.txt && python run.py"]

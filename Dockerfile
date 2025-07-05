# Second stage, copy over the requirements and install them
FROM python:3.12.9-slim
RUN apt update && apt install -y libzbar0 curl
# Install uv
RUN curl -Ls https://astral.sh/uv/install.sh | sh
# Add uv to PATH
ENV PATH="/root/.cargo/bin:$PATH"
WORKDIR /app
CMD ["sh", "-c", "uv pip install -r requirements.txt && python run.py"]

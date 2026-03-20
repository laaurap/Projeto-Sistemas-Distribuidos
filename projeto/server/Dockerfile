FROM python:3.12-slim

WORKDIR /app

RUN pip install --no-cache-dir pyzmq msgpack

COPY server.py .

ENV DB_PATH=/data/server_data.db
ENV BIND_ADDR=tcp://*:5555

VOLUME ["/data"]

EXPOSE 5555

CMD ["python", "server.py"]
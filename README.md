# VK Testing

gRPC сервис на Java для работы с key-value данными в Tarantool `3.2.x`.

API:
- `put(key, value)`
- `get(key)`
- `delete(key)`
- `range(key_from, key_to)`
- `count()`

Данные хранятся в спейсе `KV` со схемой:

```text
{name = 'key', type = 'string'}
{name = 'value', type = 'varbinary', is_nullable = true}
```

## Требования

- Java 21
- Docker Desktop
- `grpcurl` или Postman для проверки gRPC

## Запуск Tarantool

Из корня проекта:

```powershell
docker compose up -d
```

Проверить состояние:

```powershell
docker compose ps
docker compose logs tarantool
```

Остановить контейнер:

```powershell
docker compose down
```

Сбросить данные:

```powershell
docker compose down -v
```

## Запуск приложения

Из корня проекта:

```powershell
.\gradlew.bat bootRun
```

gRPC сервер поднимается на `localhost:9090`.

## Тестирование через grpcurl

Путь к `.proto`:

```text
src\main\proto
```

### Put

```cmd
grpcurl -plaintext -import-path src\main\proto -proto kv.proto -d "{\"key\":\"a\",\"value\":\"aGVsbG8=\"}" localhost:9090 kv.v1.KeyValueService/Put
```

### Get

```cmd
grpcurl -plaintext -import-path src\main\proto -proto kv.proto -d "{\"key\":\"a\"}" localhost:9090 kv.v1.KeyValueService/Get
```

### Put с `null` value

```cmd
grpcurl -plaintext -import-path src\main\proto -proto kv.proto -d "{\"key\":\"null-key\"}" localhost:9090 kv.v1.KeyValueService/Put
```

### Count

```cmd
grpcurl -emit-defaults -plaintext -import-path src\main\proto -proto kv.proto -d "{}" localhost:9090 kv.v1.KeyValueService/Count
```

### Range

```cmd
grpcurl -plaintext -import-path src\main\proto -proto kv.proto -d "{\"keyFrom\":\"a\",\"keyTo\":\"z\"}" localhost:9090 kv.v1.KeyValueService/Range
```

### Delete

```cmd
grpcurl -emit-defaults -plaintext -import-path src\main\proto -proto kv.proto -d "{\"key\":\"a\"}" localhost:9090 kv.v1.KeyValueService/Delete
```

## Тестирование через Postman

Можно использовать Postman вместо `grpcurl`.

Порядок:
- создать `gRPC` request на `localhost:9090`
- импортировать `src\main\proto\kv.proto`
- выбрать метод `kv.v1.KeyValueService/...`
- отправлять те же JSON-сообщения, что и в примерах выше

## Сборка и тесты

```powershell
.\gradlew.bat test
```

## Примечания

- `bytes` в `grpcurl` передаются как base64.
- Для `proto3` поля со значением по умолчанию могут не отображаться в ответе. Для явного вывода используйте `-emit-defaults`.
- `range` реализован как server-streaming gRPC метод.

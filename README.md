# Javagrunt GitHub App

Centralized CI/CD GitHub App that installs a shared workflow into repositories.

## What it does

When the GitHub App receives an `installation` webhook with `created` or
`repositories_added`, it:

1. Fetches `.github/workflows/ci.yml` from `dashaun-cloud/github-shared-pipelines`.
2. Creates a branch in the target repo.
3. Commits the workflow to `.github/workflows/ci.yml`.
4. Opens a pull request.

Webhook: `POST https://github.javagrunt.com/webhook/github`
OAuth callback: `https://github.javagrunt.com/login/oauth2/code/github`

## GitHub App setup

Create a GitHub App with:

- Webhook URL: `https://github.javagrunt.com/webhook/github`
- Webhook secret: set a strong secret and store it in `javagrunt.github.webhook-secret`
- Callback URL: `https://github.javagrunt.com/login/oauth2/code/github`

Recommended permissions:

- Repository contents: Read & write (to add `.github/workflows/ci.yml`)
- Pull requests: Read & write (to open a PR)
- Metadata: Read (default)

Subscribe to events:

- Installation

## Configuration

Set these via environment variables or `application.properties`:

- `javagrunt.github.app-id`
- `javagrunt.github.webhook-secret`
- `javagrunt.github.private-key-pem` or `javagrunt.github.private-key-path`
- `javagrunt.github.shared-repo-owner` (default: `dashaun-cloud`)
- `javagrunt.github.shared-repo-name` (default: `github-shared-pipelines`)
- `javagrunt.github.shared-repo-path` (default: `.github/workflows/ci.yml`)
- `javagrunt.github.shared-repo-token` (required if shared repo is private)
- `javagrunt.github.target-workflow-path` (default: `.github/workflows/ci.yml`)

## Local run

```bash
./mvnw spring-boot:run
```

## Webhook test (local)

To test the signature validation, generate the HMAC using the webhook secret:

```bash
PAYLOAD='{"action":"created","installation":{"id":123},"repositories":[{"name":"demo","full_name":"org/demo","owner":{"login":"org"}}]}'
SIG=$(printf "%s" "$PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | sed 's/^.* //')
curl -X POST http://localhost:8080/webhook/github \
  -H "X-GitHub-Event: installation" \
  -H "X-Hub-Signature-256: sha256=$SIG" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"
```

## Notes

- If a target repo already has `.github/workflows/ci.yml`, the app skips it.
- The app uses a short-lived GitHub App JWT to request installation tokens.

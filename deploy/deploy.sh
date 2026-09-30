#!/usr/bin/env bash
# Deploy na VM do backend, executado pelo .github/workflows/deploy.yml via SSH:
#   bash app/deploy.sh <tag-da-imagem>
# Sobe a tag pedida com o compose.yaml de ~/app e espera o health ficar UP (~3 min).
# Nunca lê, cria nem altera o ~/app/.env, e não imprime variáveis de ambiente.
set -euo pipefail

readonly TAG_PATTERN='^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$'
readonly HEALTH_URL='http://localhost:8080/actuator/health'
readonly HEALTH_ATTEMPTS=36
readonly HEALTH_INTERVAL_SECONDS=5

image_tag="${1:-}"
if [[ ! "$image_tag" =~ $TAG_PATTERN ]]; then
	echo "Tag de imagem inválida." >&2
	exit 1
fi

cd "$HOME/app"

if [[ ! -f .env ]]; then
	echo "$HOME/app/.env não existe; crie-o à mão (ver a seção \"Deploy em produção\" do README)." >&2
	exit 1
fi

# Registra a tag implantada sem tocar no .env; a escrita é atômica.
tmp_env="$(mktemp deploy.env.XXXXXX)"
echo "IMAGE_TAG=$image_tag" > "$tmp_env"
chmod 644 "$tmp_env"
mv "$tmp_env" deploy.env

compose() {
	docker compose --env-file .env --env-file deploy.env "$@"
}

echo "Implantando a imagem com a tag $image_tag..."
compose pull api caddy
compose up -d --remove-orphans

echo "Aguardando $HEALTH_URL responder UP..."
for ((attempt = 1; attempt <= HEALTH_ATTEMPTS; attempt++)); do
	if curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
		echo "Health UP na tentativa $attempt. Tag implantada: $image_tag"
		docker image prune -f > /dev/null
		exit 0
	fi
	sleep "$HEALTH_INTERVAL_SECONDS"
done

echo "O health não ficou UP em $((HEALTH_ATTEMPTS * HEALTH_INTERVAL_SECONDS)) s. Estado e logs da api:" >&2
compose ps >&2
compose logs --no-color --tail 200 api >&2
exit 1

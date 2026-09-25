#!/usr/bin/env bash
# Signs a release that `./mvnw -Prelease deploy` staged, bundles it and uploads
# the bundle to the Central Portal, then waits for the Portal's verdict.
#
#   .github/central-publish.sh <staging directory> <version>
#
# The signing key is already in gpg's keyring. From the environment:
#   GPG_PASSPHRASE            the key's passphrase; empty for a key without one
#   CENTRAL_TOKEN_USERNAME    a Portal user token, both halves
#   CENTRAL_TOKEN_PASSWORD
#   CENTRAL_PUBLISHING_TYPE   USER_MANAGED (default): validated, then published
#                             from the Portal's page; AUTOMATIC: published as
#                             soon as it validates
#   CENTRAL_DRY_RUN           true: build and check the bundle, upload nothing
set -euo pipefail

staging=$(cd "$1" && pwd)
version=$2
portal=https://central.sonatype.com/api/v1/publisher

case $version in
*-SNAPSHOT) echo "refusing to publish snapshot $version" >&2; exit 1 ;;
esac

bundle=$(mktemp -d)
trap 'rm -rf "$bundle"' EXIT

# Every file of the release, with the checksums Maven wrote beside it. Not
# Maven 4's build POM, which is for the reactor that wrote it, and not the
# repository metadata, which Central keeps itself.
(cd "$staging" && find . -path "*/$version/*" -type f ! -name '*-build.pom*') |
	while read -r file; do
		mkdir -p "$bundle/$(dirname "$file")"
		cp "$staging/$file" "$bundle/$file"
	done

count=$(find "$bundle" -type f | wc -l)
if [ "$count" -eq 0 ]; then
	echo "nothing staged for $version under $staging" >&2
	exit 1
fi

# A signature beside every file but the checksums.
find "$bundle" -type f ! -name '*.md5' ! -name '*.sha1' | sort | while read -r file; do
	gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
		--armor --detach-sign "$file" <<<"${GPG_PASSPHRASE:-}"
	gpg --batch --verify "$file.asc" "$file" 2>/dev/null
done

zip=$staging/central-bundle-$version.zip
rm -f "$zip"
(cd "$bundle" && zip -qr "$zip" .)
echo "bundle: $zip"
unzip -l "$zip"

if [ "${CENTRAL_DRY_RUN:-}" = true ]; then
	echo "dry run: nothing uploaded"
	exit 0
fi
if [ -z "${CENTRAL_TOKEN_USERNAME:-}" ] || [ -z "${CENTRAL_TOKEN_PASSWORD:-}" ]; then
	echo "no Portal token in CENTRAL_TOKEN_USERNAME and CENTRAL_TOKEN_PASSWORD" >&2
	exit 1
fi

type=${CENTRAL_PUBLISHING_TYPE:-USER_MANAGED}
auth="Authorization: Bearer $(printf '%s:%s' "$CENTRAL_TOKEN_USERNAME" "$CENTRAL_TOKEN_PASSWORD" | base64 -w0)"

id=$(curl -sS --fail-with-body -H "$auth" -F "bundle=@$zip" \
	"$portal/upload?name=sbe-buddy-$version&publishingType=$type")
echo "deployment $id, $type"

# Validation takes minutes; publishing, for AUTOMATIC, longer.
for _ in $(seq 1 120); do
	status=$(curl -sS --fail-with-body -X POST -H "$auth" "$portal/status?id=$id")
	state=$(jq -r .deploymentState <<<"$status")
	echo "$(date -u +%H:%M:%S) $state"
	case $state in
	FAILED)
		jq . <<<"$status" >&2
		exit 1
		;;
	VALIDATED)
		if [ "$type" = USER_MANAGED ]; then
			echo "validated: publish it at https://central.sonatype.com/publishing/deployments"
			exit 0
		fi
		;;
	PUBLISHED)
		echo "published: net.concini:*:$version"
		exit 0
		;;
	esac
	sleep 15
done
echo "deployment $id still $state after 30 minutes; see https://central.sonatype.com/publishing/deployments" >&2
exit 1

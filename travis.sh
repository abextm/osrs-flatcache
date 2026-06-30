#!/bin/bash

set -e -x

./gradlew build

BRANCH=""
if [[ -n ${DO_RELEASE_TAG+x} ]]; then
	BRANCH="--branch $DO_RELEASE_TAG"
fi

git clone --depth 1 $BRANCH https://github.com/abextm/osrs-cache.git osrs-cache
mkdir dump
./gradlew :packer:run --args="dump all osrs-cache dump"

if [[ -n ${DO_RELEASE_TAG+x} ]]; then
	export VER="$(./gradlew :packer:printRuneliteVersion -q)"
	pip3 install PyGithub
	export ASSET_NAME="dump-$DO_RELEASE_TAG.tar.gz"
	tar -zcf "$ASSET_NAME" dump
	python3 <<EOF
import os
from github import Github, UnknownObjectException

g = Github(os.environ['GITHUB_TOKEN'])
r = g.get_repo("abextm/osrs-cache")

tag = os.environ['DO_RELEASE_TAG']
try:
	rel = r.get_release(tag)
except UnknownObjectException:
	rel = r.create_git_release(tag, tag, "")

rel.upload_asset(os.environ['ASSET_NAME'], "Dump with flatcache "+os.environ['VER'], 'application/octet-stream')

EOF
fi

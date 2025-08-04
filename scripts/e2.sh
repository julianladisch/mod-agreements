if [ $# -eq 0 ]; then
  echo "No arguments supplied"
else
  if [ -f .okapirc ]; then
    . .okapirc
  elif [ -f $HOME/.okapirc ]; then
    . $HOME/.okapirc
  else
    echo You must configure \$HOME/.okapirc
    echo export IS_SECURE_SUPERTENANT=Y
    echo export ST_UN=sysadm
    echo export ST_PW=PASSWORD_FROM_LOCAL_okapi_commander_cfg.json
    echo export OKAPI_URL=http://localhost:30100
    exit 0
  fi

  TENANT_PARAM="$TENANT"
  if [ "$2" ]; then
    TENANT_PARAM=$2
  fi

  curl -H "X-OKAPI-TENANT: $TENANT_PARAM" -XDELETE "http://localhost:${1}/_/tenant"
  curl -XPOST -H 'Content-Type: application/json' -H "X-OKAPI-TENANT: $TENANT_PARAM" "http://localhost:${1}/_/tenant" -d '{ "parameters": [{"key": "loadSample", "value": "test"}, {"key": "loadReference", "value": "other"}]}'
fi

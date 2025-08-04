#!/bin/bash

curl -v -v -v --header "X-Okapi-Tenant: diku" --header "X-Okapi-Token: dummyToken" --header 'X-Okapi-Permissions: ["ONE"]' --header "X-Okapi-User-Id: 1234" http://localhost:8080/erm/sas -X GET

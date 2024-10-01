# mod-agreements

Copyright (C) 2018-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Introduction - developers looking to enhance the resources that mod-agreements provides

Mod-Agreements is a FOLIO module to manage agreements which control an institutions access to resources. Normally, this will be 
subscribed access to packages of electronic resources, although the aim is to make the affordances offered by mod-agreements as general as possible.

Mod-Agreements can create agreements that control access to content bundled into packages and defined in knowledgebase systems, it can 
identify specific electronic or print resources, and act as a bridge between those resources and associated licenses and purchase documents.

Developers looking to access the services exposed by mod-agreements can find more information in the following sections

## Module installation and upgrade notes

The module has important dependences on reference data. initial installations and module upgrades should specify loadReference=true. The module
may not work as expected if this is omitted.

## Resources exposed by this module

### /erm/sas resource - SubscriptionAgreements

The /erm/sas resource allows module clients to Create, Retrieve, Update and Delete SubscriptionAgreement entities, and to search for SAs. [See the documentation](./doc/subscription_agreement_resource.md)

### /erm/refdataValues

This resource allows module owners to obtain tenant extensible lists of controlled values. The URL pattern is "/erm/refdataValues/$domain/$property" 

As of 2019-02-20 the following are defined:

| Refdata Category | URL for values |
| --- | --- |
|TitleInstance.SubType|/erm/refdataValues/TitleInstance/SubType|
|TitleInstance.Type|/erm/refdataValues/TitleInstance/Type|
|InternalContact.Role|/erm/refdataValues/InternalContact/Role|
|SubscriptionAgreementOrg.Role|/erm/refdataValues/SubscriptionAgreementOrg/Role|
|SubscriptionAgreement.AgreementType|/erm/refdataValues/SubscriptionAgreement/AgreementType|
|SubscriptionAgreement.RenewalPriority|/erm/refdataValues/SubscriptionAgreement/RenewalPriority|
|Global.Yes_No|/erm/refdataValues/Global/Yes_No|
|SubscriptionAgreement.AgreementStatus|/erm/refdataValues/SubscriptionAgreement/AgreementStatus|
|Pkg.Type|/erm/refdataValues/Pkg/Type|
|IdentifierOccurrence.Status|/erm/refdataValues/IdentifierOccurrence/Status|

## ModuleDescriptor

https://github.com/folio-org/mod-agreements/blob/master/service/src/main/okapi/ModuleDescriptor-template.json

# For module developers looking to extend or modify the resources presented by this module

This is the main starter repository for the Grails-based OLF - ERM backend modules.

- [Getting started](service/docs/getting-started.md "Getting started")

There are a couple of differing ways to run this module as a developer, either via the (now defunct) vagrant images, or directly against an internal postgres via docker compose.

Longer term there may be official rancher environments offered for dev, but our current workflow does not include those.

## Additional information

### Issue tracker

See project [ERM](https://issues.folio.org/projects/ERM)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

## Docker compose
This is the current recommended way to develop against this module, with a couple of key caveats. This setup runs the module standalone, without the okapi layer or the rest of FOLIO.
Obviously this prevents full testing and development of permission based issues or any app interaction, but is sufficient for developing anything internal to the module.

### Setup
The devloper will need `docker compose`, which should now be included in relatively new versions of `docker`. If not, either look to update docker or install the compatible standalone `docker-compose`. (Note that this will change the necessary commands later from `docker compose` to `docker-compose`)

### Run command
First ensure that the postgres is up and running, by navigating to `mod-agreements/tools/testing` and running

    docker compose up
Then navigate in a separate terminal to `mod-agreements/service` and run with the `dc` profile

    ./gradlew -Dgrails.env=dc bootRun

This will run the module on port 8080, and it can be directly `curl`ed to on that port without having to pass through an okapi layer.


## Vagrant
Using the now defunct vagrant images for snapshot allows for a full FOLIO system running in a virtual machine. This is highly costly resource-wise, and the lack of up to date images creates issues for development. However it is listed below for posterity.

### Run command
    ./gradlew -Dgrails.env=vagrant-db bootRun

### Initial Setup
Most developers will run some variant of the following commands the first time through

#### In window #1

Start the vagrant image up from the project root

    vagrant destroy
    vagrant up

Sometimes okapi does not start cleanly in the vagrant image - you can check this with

    vagrant ssh

then once logged in

    docker ps

should list running images - if no processes are listed, you will need to restart okapi (In the vagrant image) with

    sudo su - root
    service okapi stop
    service okapi start

Finish the part off with

    tail -f /var/log/folio/okapi/okapi.log

#### In window #2

Build and run mod-agreements stand alone

    cd service
    grails war
    ../scripts/run_external_reg.sh

#### In window #3

Register the module and load some test data

  cd scripts
  ./register_and_enable.sh
  ./dev_submit_pkg.sh
  ./dev_trigger_kb_sync.sh 

#### In window #4

Run up a stripes platform containing erm

See [UI Agreements quick start](https://github.com/folio-org/ui-agreements/blob/master/README.md)

This section is run in a local setup, not from any particular checked out project, YMMV

    cd ../platform/stripes/platform-erm
    stripes serve ./stripes.config.js --has-all-perms



You should get back

Waiting for webpack to build...
Listening at http://localhost:3000

and then be able to access the app

## Kubernetes Deployment Notes

You may need to set `OKAPI_SERVICE_PORT` and/or `OKAPI_SERVICE_HOST` on the mod-agreements container. 

If you create a Service in Kubernetes named "okapi" and expose a port for Hazelcast and a port for HTTP; the Hazelcast port might be the default and mod-agreements will use that default.



## Validating module descriptor
There is a github action created to run the module descriptor validation on `push`, but if a developer wishes to run the validation locally there is some setup that needs doing. The validation script is a Maven plugin which does not work natively with our gradle based apps.
### Maven
Developer will need maven cli `mvn` installed on their machine.
### settings.xml
Create a `settings.xml` file within the "service" directory (DO NOT MERGE THIS) containing the following:
```
<settings>
  <profiles>
    <profile>
      <id>folioMavenProfile</id>
      <pluginRepositories>
        <pluginRepository>
          <id>folio-nexus</id>
          <name>FOLIO Maven repository</name>
          <url>https://repository.folio.org/repository/maven-folio</url>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>folioMavenProfile</activeProfile>
  </activeProfiles>
</settings>
```
### Running
Finally the developer can run this command from the root directory (ie `mod-agreements` not `mod-agreements/service`)
```
mvn org.folio:folio-module-descriptor-validator:1.0.0:validate -DmoduleDescriptorFile=service/src/main/okapi/ModuleDescriptor-template.json -s service/settings.xml -l validate_module_descriptor_output.txt
```

This will create a file called `validate_module_descriptor_output.txt` containing the output of the validator. The github action does some cleanup and comments the errors on a PR (if present). The `grep`/`sed` commands with regex can be found in the workflow file `.github/validate-module`.
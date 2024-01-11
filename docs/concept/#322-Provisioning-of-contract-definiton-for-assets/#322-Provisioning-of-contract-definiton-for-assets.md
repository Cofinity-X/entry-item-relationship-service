# \[Concept\] \[#ID#\] Summary 

| Key           | Value             |
|---------------|-------------------|
| Creation date | 11.01.2024        |
| Ticket Id     | https://github.com/eclipse-tractusx/item-relationship-service/issues/322        |    
| State        | <DRAFT,WIP, DONE> | 

# LOP <TO be deleted>
- [ ] Can Trace-X access the management API of the IRS EDC?
- [ ] Should the EDC Management API of the IRS be configured in the business application or should the url be supplied via the JobResponse?
- [ ] Does the business app have access to the management API of the EDC configured for the IRS

# Table of Contents
1. [Overview](#overview)
2. [Summary](#summary)
3. [Problem Statement](#statement)
4. [Requirements](#requirements)
5. [NFR](#nfr)
6. [Out of scope](#outofscope)
7. [Concept](#concept)
8. [Glossary](#glossary)


# <ins>Overview</ins> <a name="overview"></a>
The exchange of assets via the EDC takes place after a successful contract negotiation in which it is checked whether the consumer has authorized access to the data asset.
This access is automatically checked by the EDC via so-called AccessPolicies. The consumer is only granted access to the data after a successful check.
During contract negotation the edc stores audits the following artefacts edc:ContractAgreement and edc:ContractNegotation these audit information could be requested over the edc management api. 
To request the mentioned artefacts over the management api the ContractAgreementDto:@id is required.
This specific id must therefore be stored and linked for the exchanged asset in order to be able to determine the corresponding contract agreement later on.

# <ins>Summary</ins> <a name="summary"></a>

# <ins>Problem Statement</ins> <a name="statement"></a>
1. The ContractAgreementDto:@id is currently not delivered via the IRS response, so business apps that use the IRS cannot access the corresponding ContractAggreement under which the assets delivered by the IRS were exchanged.
2. The EDC management api does not provide any endpoint to query a contract agreement using an asset id.
3. Business apps must make the contract agreements under which the assets were exchanged available for audit purposes.

# <ins>Requirements</ins> <a name="requirements"></a>

1. [ ] Provisioning of ContractAgreementDto:@id via IRS JobReponse for AAS retrieved via EDC.
2. [ ] Provisioning of ContractAgreementDto:@id via IRS JobReponse for submodels retrieved via EDC.
3. [ ] API parameter SHOULD control whether ContractAgreementDto:@id should be returned via the IRSJobResponse

# <ins>NFR</ins> <a name="nfr"></a>

# <ins>Out of scope</ins> <a name="outofscope"></a>

# <ins>Concept</ins> <a name="concept"></a>


## ContractAgreementId for submodels 

### Option 1: Provide submodel for  contractAgreementId 
Impact: High invasive changes to the code, api, JobResponse and documentation. 

```json 
  "contractAggreements" : [
    "contractAggreement" : {
       "contractAgreementId": "<contractAgreementId>",
       "submodels": [
        {
            "identification": "urn:uuid:f9b6f066-c4de-4bed-b531-2a1cad7bd173",
            "aspectType": "urn:bamm:io.catenax.single_level_bom_as_built:1.0.0#SingleLevelBomAsBuilt",
            "payload": {
              <... submodel payload ...>
        }
      ] 
    }     
        
  ]
```

### Option 2: Provide contractAgreementId for each submodel: 
Impact: 
- Redundant information in case multiple submodels were received for the same contractAgreementId. 
- JobReponse size is already critical and extends by ~100byte multiples with every submodel and aas shell stored in the JobReponse  

```json 
	"submodels": [
      
      {
        "identification": "urn:uuid:f9b6f066-c4de-4bed-b531-2a1cad7bd173",
        "aspectType": "urn:bamm:io.catenax.single_level_bom_as_built:1.0.0#SingleLevelBomAsBuilt",
        "contractAgreementId": "<contractAgreementId>",
        "payload": {
            <... submodel payload ...>
      }
  ]
```

# <ins>Glossary</ins> <a name="glossary"></a>

| Abbreviation           | Name                     |
|------------------------|--------------------------|
| edc:ContractAgreement  |                          |
| edc:ContractNegotation |                          |
| AAS                    | AssetAdministrationShell |   
 | contractAgreementId   |                          |
An example project to demonstrate Java connectivity to SAP On-Premise system from SAP Cloud Foundry Environment using PrincipalPropagation. The Java app must be bound to Connectivity, XSUAA, and Destination service. To access the application, ensure the incoming request has an Authorization header which will be used to propagate user. For BasicAuthentication, use basic auth scheme (username:password) instead of the token when setting the 'SAP-Connectivity-Authentication' header. 

Use following command for deployment:
 cf push onpremisebridge -p target/onpremiseproxybridge-0.0.1-SNAPSHOT.jar -m 1G


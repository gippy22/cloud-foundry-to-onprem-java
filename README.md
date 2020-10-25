Use command following command for deployment
 cf push onpremisebridge -p target/onpremiseproxybridge-0.0.1-SNAPSHOT.jar -m 1G

Debugging
    cf ssh onpremisebridge -c "app/META-INF/.sap_java_buildpack/sapjvm/bin/jvmmon"
    cf ssh onpremisebridge -N -T -L 8000:127.0.0.1:8000
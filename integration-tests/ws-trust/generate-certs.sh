#!/bin/bash

set -e
set -x

keySize=2048
days=10000
password="password"
encryptionAlgo="aes-256-cbc"

workDir="target/openssl-work"
destinationDir="src/main/resources"

if [[ -n "${JAVA_HOME}" ]] ; then
  keytool="$JAVA_HOME/bin/keytool"
elif ! [[ -x "$(command -v keytool)" ]] ; then
  echo 'Error: Either add keytool to PATH or set JAVA_HOME' >&2
  exit 1
else
  keytool="keytool"
fi

if ! [[ -x "$(command -v openssl)" ]] ; then
  echo 'Error: openssl is not installed.' >&2
  exit 1
fi

mkdir -p "$workDir"
mkdir -p "$destinationDir"

# Certificate authority
openssl genrsa -out "$workDir/cxfca.key" $keySize
openssl req -x509 -new -subj '/O=apache.org/OU=eng (NOT FOR PRODUCTION)/CN=cxfca' -key "$workDir/cxfca.key" -nodes -out "$workDir/cxfca.pem" -days $days -extensions v3_req
openssl req -new -subj '/O=apache.org/OU=eng (NOT FOR PRODUCTION)/CN=cxfca' -x509 -key "$workDir/cxfca.key" -days $days -out "$workDir/cxfca.crt"

for actor in client service sts; do
  # Generate keys
  openssl genrsa -out "$workDir/$actor.key" $keySize

  # Generate certificates
  openssl req -new -subj "/O=apache.org/OU=eng (NOT FOR PRODUCTION)/CN=$actor" -key "$workDir/$actor.key"  -out "$workDir/$actor.csr"
  openssl x509 -req -in "$workDir/$actor.csr" -CA "$workDir/cxfca.pem" -CAkey "$workDir/cxfca.key" -CAcreateserial -days $days -out "$workDir/$actor.crt"

  # Export keystores
  openssl pkcs12 -export -in "$workDir/$actor.crt" -inkey "$workDir/$actor.key" -certfile "$workDir/cxfca.crt" -name "$actor" -out "$destinationDir/$actor.pkcs12" -passout pass:"$password" -keypbe "$encryptionAlgo" -certpbe "$encryptionAlgo"
done

keytool -import -trustcacerts -alias sts     -file "$workDir/sts.crt"     -noprompt -keystore "$destinationDir/service.pkcs12"  -storepass "$password"

keytool -import -trustcacerts -alias service -file "$workDir/service.crt" -noprompt -keystore "$destinationDir/sts.pkcs12"      -storepass "$password"
keytool -import -trustcacerts -alias client  -file "$workDir/client.crt"  -noprompt -keystore "$destinationDir/sts.pkcs12"      -storepass "$password"

keytool -import -trustcacerts -alias service -file "$workDir/service.crt" -noprompt -keystore "$destinationDir/client.pkcs12"   -storepass "$password"
keytool -import -trustcacerts -alias sts     -file "$workDir/sts.crt"     -noprompt -keystore "$destinationDir/client.pkcs12"   -storepass "$password"


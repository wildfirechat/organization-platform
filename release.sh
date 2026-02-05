set -e
cd organization-web
npm install
npm run build
cd ..

cd organization-server
mvn clean package
cd ..
ls organization-server/target/organization-platform-server*.jar

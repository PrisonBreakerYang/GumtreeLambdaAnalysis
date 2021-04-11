APP_MAINCLASS=com.github.gumtreelambdaanalysis.RemainedLambdaFinder
CLASSPATH=target/GumtreeLambdaAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar:/data/lambda/jdk-14/lib

mvn clean
mvn compile
mvn package

java -ea -cp ${CLASSPATH} {APP_MAINCLASS}
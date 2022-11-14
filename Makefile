PATH := node_modules/.bin:$(PATH)

m1p.jar: src/m1p/*
	rm -f m1p.jar && clj -A:jar

deploy: m1p.jar
	mvn deploy:deploy-file -Dfile=m1p.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml

.PHONY: deploy

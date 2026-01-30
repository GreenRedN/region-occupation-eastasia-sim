# GitHub 웹 업로드 시 숨김 파일(.로 시작) 때문에 누락되는 항목들

GitHub 웹(Upload files)로 폴더를 올리면 `.gitignore`, `.gitattributes`, `.mvn/**` 처럼 **점(.)으로 시작하는 파일/폴더**는
'hidden' 처리되어 업로드 대상에서 빠질 수 있습니다.

이 프로젝트는 Maven Wrapper(`mvnw`)를 쓰기 때문에, 아래 파일들을 **GitHub에서 직접 생성**해 주세요.

---

## 1) `.gitignore`

```gitignore
# ---- Build outputs ----
/target/
*.class
*.log

# ---- OS ----
.DS_Store
Thumbs.db

# ---- IntelliJ IDEA ----
.idea/
*.iml
*.iws
*.ipr
out/

# ---- VS Code ----
.vscode/

# ---- Eclipse / STS ----
.classpath
.project
.settings/
.factorypath
.apt_generated/
.springBeans
.sts4-cache/

# ---- NetBeans ----
/nbproject/private/
/nbbuild/
/dist/
/nbdist/
/.nb-gradle/

# ---- Maven Wrapper ----
.mvn/wrapper/maven-wrapper.jar

# ---- Safety: never commit secrets ----
.env
*.pem
*.key
```

## 2) `.gitattributes`

```gitattributes
/mvnw text eol=lf
*.cmd text eol=crlf
```

## 3) `.mvn/wrapper/maven-wrapper.properties`

(새 파일 생성 시 경로를 그대로 입력하면 폴더도 같이 만들어집니다)

```properties
# Maven Wrapper properties
# - mvnw will download Maven distribution if not present.
# - requires internet access on first run.

distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar
```

## 4) `.mvn/wrapper/.gitignore`

```gitignore
/maven-wrapper.jar
```

---

## GitHub에서 파일 만드는 방법(정확히)

1. 레포지토리에서 **Add file → Create new file**
2. 파일 이름 칸에 예시처럼 **경로 포함**해서 입력  
   - `.mvn/wrapper/maven-wrapper.properties`
3. 아래 내용을 그대로 붙여넣고 **Commit**

> `target/` 폴더는 빌드 산출물이라 레포에 올릴 필요가 없어서 이 패키지에서는 제거해뒀습니다.

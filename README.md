# MJU 그림판 (Drawing Program)

명지대학교 **패턴중심사고와프로그래밍** 수업의 최종 프로젝트입니다.
Java Swing으로 만든 벡터 기반 그림판 프로그램입니다.

## 주요 기능

- **도형 그리기**: 직선, 사각형, 타원, 다각형, 자유곡선(Freehand), 텍스트 박스
- **도형 편집**: 선택, 이동, 크기 조절, 회전
- **그룹**: 여러 도형을 묶기(Group) / 풀기(Ungroup)
- **편집 메뉴**: Undo / Redo, Cut / Copy / Paste, Delete
- **속성 변경**: 선 색, 채움 색, 선 스타일, 폰트 스타일
- **파일 메뉴**: 새 창, 열기, 저장, 다른 이름으로 저장, 인쇄, 종료
- **다국어 UI**: 한국어 / English / 日本語 (XML 설정 파일로 분리)
- **연관 도형 추천 (v0.1)**: 자유선을 그리면 오른쪽 사이드바에 비슷한 도형(직선/사각형/타원/삼각형/다각형)을
  점수와 함께 추천하고, 클릭하면 그 도형으로 바꿔 준다. 기하 휴리스틱이 기본이고,
  로컬 LLM(Ollama)이 떠 있으면 그 결과를 합산한다. Ollama 가 없어도 동작한다.

## 실행 방법

### 요구 사항
- Java 17 이상

### jar로 실행
[Releases](https://github.com/JungMK0720/MJU_4-1/releases)의 `v0.0`에서 `MJU_Paint.jar`를 받은 뒤:

```bash
java -jar MJU_Paint.jar
```

### 소스에서 실행
1. 이 저장소를 clone 합니다.
2. Eclipse에서 `File > Import > Existing Projects into Workspace`로 불러옵니다.
3. `src/global/GMain.java`를 실행합니다.

터미널에서 실행하려면 (프로젝트 루트에서):

```bash
javac -encoding UTF-8 -d bin $(find src -name '*.java')
java -cp bin global.GMain
```

### 로컬 LLM 추천 (선택 사항)
[Ollama](https://ollama.com)를 설치하고 모델을 받아 두면 추천 사이드바가 로컬 LLM 결과를 함께 반영합니다.
외부 네트워크로는 아무것도 보내지 않습니다.

```bash
ollama pull llama3.2
ollama serve          # 기본 주소 http://localhost:11434
```

주소/모델/타임아웃은 `src/rsc/*Config.xml` 의 `<GRecommender ...>` 또는 시스템 속성으로 바꿀 수 있습니다.

```bash
java -Dpaint.ollama.model=llama3.1 -Dpaint.ollama.url=http://localhost:11434 -cp bin global.GMain
```

> 파일 저장/열기의 기본 경로가 `src/global/GConstants.java`의 `DEFAULT_DIR`에
> 개발 환경 경로(`E:\4-1\MJU_Pattern\Drawing`)로 지정되어 있습니다.
> 다른 PC에서 실행할 때는 이 값을 본인 환경에 맞게 수정하세요.

## 프로젝트 구조

```
src
├─ global        # 진입점(GMain), 상수 및 XML 설정 로더(GConstants)
├─ frames        # 메인 프레임, 메뉴바, 툴바, 그리기 패널
├─ menus         # 파일 메뉴 동작
├─ manager       # 패널/프로세스/컨테이너 관리
├─ shapes        # 도형 클래스 (GShape 상속 구조)
├─ transformer   # 그리기·이동·크기 조절·회전 로직
├─ recommend     # 자유선 특징 추출, 추천 전략(기하 휴리스틱 / Ollama), JSON 파서
└─ rsc           # 언어별 설정 (enConfig / koConfig / jpConfig .xml)
docs
└─ ARCHITECTURE_REVIEW.md   # 구조 진단, 버그 원인(FIX-01~14), OOP/Facade 평가, 개선 로드맵
```

## 설계 포인트

- **도형 계층 구조**: `GShape`를 부모로 `GLine`, `GRectangle`, `GEllipse`, `GPolygon`, `GFreeLine`, `GTextArea`, `GGroup`이 상속
- **Transformer 패턴**: 그리기/이동/크기 조절/회전을 `GTransformer` 하위 클래스(`GDrawer`, `GMover`, `GResizer`, `GRotater`)로 분리해 마우스 이벤트 처리를 공통화
- **설정 외부화**: 메뉴·툴바 라벨과 툴팁을 XML에서 읽어 코드 수정 없이 언어 변경
- **좌표계 분리 (v0.1)**: 이동/회전은 world 좌표(`preConcatenate`), 리사이즈는 local 좌표에서 처리해
  이동·회전·확대를 섞어도 중심과 고정점이 어긋나지 않음 (`docs/ARCHITECTURE_REVIEW.md` FIX-01)
- **Composite 그룹 (v0.1)**: `GGroup`이 변환을 자식에게 전달하므로 변환기(Transformer)는 그룹과 낱개 도형을 구분하지 않음
- **추천 사이드바 (v0.1)**: Strategy(`GShapeRecognizer`) + Observer(`GRecommendListener`), 백그라운드 스레드(SwingWorker), 외부 라이브러리 없음

## 버전 기록

| 버전 | 내용 |
|---|---|
| v0.0 | 수업 제출 최종 결과물을 기준 버전으로 릴리스 |
| v0.1 | 회전/앵커/그룹/undo/선택 등 버그 14건 수정(FIX-01~14), 자유선 연관 도형 추천 사이드바 추가(FEAT-01), 구조 진단 문서 추가 |

## 만든 사람

정민규 

## 년도

2025년 1학기

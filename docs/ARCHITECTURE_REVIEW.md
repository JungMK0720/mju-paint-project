# MJU 그림판 구조 진단 및 개선 보고서

- 작성일: 2026-10-18
- 작성자: Jung Minkyu
- 대상 코드: `src/` 전체 (약 3,400 LOC → 수정 후 약 5,000 LOC)
- 이 문서의 목적: (1) 현재 구조의 문제를 진단하고, (2) 로테이션·앵커·그룹화 버그의 "원인"을 설명하고,
  (3) 객체지향/Facade 관점에서 평가하고, (4) 포트폴리오로 발전시킬 때 무엇을 강조할지 정리한다.
- 코드의 모든 수정 지점에는 `[2026-10-18 수정자: Jung Minkyu] FIX-nn` 또는 `FEAT-01` 주석과
  `As-Is / To-Be / 연관` 항목을 남겼다. `grep -rn "FIX-0" src` 로 전부 찾을 수 있다. (부록 A 참고)

---

## 0. 결론 요약

| 항목 | 결론 |
|---|---|
| 로테이션/앵커 버그의 원인 | **좌표계 혼용**. 중심·고정점은 world 좌표(`getBounds()`)로 구하고, 적용은 `AffineTransform.rotate/translate/scale` (post-concatenate = **local** 좌표계)로 했다. 도형을 한 번이라도 이동/확대하면 local ≠ world 라서 중심이 어긋난다. (FIX-01) |
| 그룹화 버그의 원인 | **Composite 가 반쪽**. `GGroup` 은 자기 `affineTransform` 을 그리지 않는데 Resizer/Rotater 는 그것만 바꿨고, Mover 만 `instanceof GGroup` 으로 자식을 직접 돌았다. 자식의 선택 플래그도 남아 있었다. (FIX-07) |
| "이동하면 선택이 풀린다" 의 원인 | 선택 도구의 러버밴드 사각형을 **도형 목록에 넣었다 빼는** 구조. 클릭-이동 때는 러버밴드가 0 크기라 `contains()` 가 모두 false → 전부 선택 해제. (FIX-02) |
| 객체지향 평가 | 상속·다형성(GShape, GTransformer)은 잘 썼다. 그러나 `getAffineTransform()` 로 내부 상태를 노출해 캡슐화가 깨졌고, 그 결과가 FIX-01 이다. `GDrawingPanel` 은 모델·히스토리·입력·뷰를 모두 가진 God class 다. |
| Facade 평가 | 툴바/메뉴가 `GDrawingPanel` 의 public 메서드만 호출하는 점은 Facade "처럼" 보이지만, 패널이 서브시스템을 감싸는 게 아니라 **스스로 서브시스템 전체**라서 진짜 Facade 가 아니다. (3장) |
| 과제로서의 평가 (의견) | 수업 과제 기준으로 기능 범위·패턴 적용 의도는 충분하다. 포트폴리오 기준으로는 "버그를 원인까지 추적해 고친 과정" 과 "LLM 추천 사이드바" 를 중심으로 재구성해야 가치가 생긴다. (5장) |
| 이번에 한 일 | 버그 14건 수정(FIX-01~14), 추천 사이드바 1건 추가(FEAT-01), 회귀 테스트 하네스로 검증. |

---

## 1. 현재 구조 진단

### 1.1 전체 구조 (수정 후 기준)

```
GMain ─ GConstants.readFromFile(xml)          # 설정 외부화 (i18n)
  └─ GMainFrame (JFrame, BorderLayout)
       ├─ NORTH  GContainer ← GShapeToolBar / GFunctionToolBar / GColorToolBar
       ├─ CENTER GDrawingPanel  ← 모델(shapes) + 선택 + undo/redo + 마우스 상태기계 + 그리기
       ├─ EAST   GRecommendPanel (FEAT-01) ← recommend.* (Strategy: 기하 휴리스틱 / Ollama)
       ├─ SOUTH  GBelowPanel (탭) ── GPanelManager (패널 교체·연결)
       └─ MenuBar GMenuBar ── GFileMenu (저장/열기/인쇄, Java 직렬화)
shapes.*      GShape(추상) ← GRectangle, GEllipse, GLine, GFreeLine, GPolygon, GTextArea, GGroup(Composite)
transformer.* GTransformer(추상) ← GDrawer, GMover, GResizer, GRotater
```

### 1.2 잘 된 점 (사실)

1. **도형 계층 + 다형성**: `GDrawingPanel.paintComponent` 는 `shape.draw(g)` 만 부른다. 새 도형은 `GShape` 를 상속하고 `EShapeTool` 에 한 줄 추가하면 된다 (리플렉션 팩토리 `newShape()`).
2. **변환 로직의 분리**: 그리기/이동/리사이즈/회전을 `GTransformer` 하위 클래스로 나눠 마우스 이벤트 처리 코드가 `start/drag/finish` 로 공통화돼 있다. (Strategy + State 의 중간 형태)
3. **설정 외부화**: 메뉴/툴바/툴팁/색 팔레트가 XML 에 있고 enum 의 `setValues(Node)` 가 읽어 들인다. 3개 언어를 코드 수정 없이 바꾼다.
4. **AffineTransform 기반 변환**: 도형의 원본 좌표를 바꾸지 않고 변환 행렬만 누적한다. 방향은 맞다(버그는 좌표계 혼용이지 설계 자체가 아니다).

### 1.3 문제점 (사실) — 이번에 고친 것

| ID | 증상 | 원인 | 수정 위치 |
|---|---|---|---|
| FIX-01 | 이동 후 회전하면 도형이 공전함, 회전 후 이동이 엉뚱한 방향, 이동 후 리사이즈 시 점프 | world 좌표로 구한 중심/고정점을 local 좌표계 연산(post-concatenate)에 넣음 | `GShape.translate/rotate/scaleLocal/toLocal`, `GMover`, `GRotater`, `GResizer` |
| FIX-02 | 이동/리사이즈가 끝나면 선택이 풀려 매번 러버밴드로 다시 선택해야 함 | 러버밴드가 `shapes` 안에 있었고, 클릭 변환 때는 0 크기라 `contains()` 전부 false | `GDrawingPanel.rubberBand`, `startTransform/finishTransform/paintComponent` |
| FIX-03 | Ctrl+Z 를 한 번 누르면 아무 변화 없고 두 번 눌러야 한 단계 돌아감 | 모든 곳에서 "변경 **후**" 스냅샷을 찍어 undo top == 현재 상태 | `GDrawingPanel.saveStateForUndo()` 호출 위치 전부 |
| FIX-04 | 선택 도구에서 더블클릭하면 마지막 도형이 사라지거나 NPE | 상태 무관하게 `finishTransform` → `shapes.remove(size-1)` | `MouseEventHandler.mouse2Clicked` |
| FIX-05 | 겹친 도형에서 위에 보이는 것을 클릭해도 아래 것이 잡힘 | `onShape` 가 0번부터 순회 | `GDrawingPanel.onShape` (역순) |
| FIX-06 | 선/자유선을 클릭으로 선택할 수 없음 | `Shape.contains(x,y)` 는 면적 판정. `Line2D` 는 항상 false, `GeneralPath` 는 "안쪽" 만 true | `GShape.hitBody()` (stroke 허용 오차), GLine/GFreeLine/GTextArea 의 중복 override 제거 |
| FIX-07 | 그룹은 이동만 되고 리사이즈/회전 불가, 그룹 안에 자식 앵커가 보임 | 그룹의 AT 는 아무도 안 그림, Mover 만 instanceof 분기, add 시 자식 선택 해제 안 함 | `GGroup` (translate/rotate/scaleLocal 자식 전달, getLocalBounds, 앵커), `GDrawingPanel.group/ungroup` |
| FIX-08 | 복사(Ctrl+C) 가 러버밴드로 고른 것만 복사됨 | `selectedShapes` Vector 와 `isSelected()` 플래그, 두 개의 "선택" | Vector 제거, `getSelectedShapes()` 단일화 |
| FIX-09 | 글 상자를 옮긴 뒤 러버밴드/리사이즈가 원래 자리를 봄, 선택해도 앵커 없음 | `GTextArea.getBounds()` 가 변환 전 사각형 반환 | override 제거, `draw` 에 앵커 |
| FIX-10 | 수평선 앵커를 끌면 선이 사라짐 | 높이 0 → `h2/0 = NaN` 이 AT 에 들어감 | `GResizer.MIN_SIZE` 가드 |
| FIX-11 | 파일 메뉴 마지막 항목이 빈 라벨이고 눌러도 종료 안 됨, 메뉴 이름 다국어 안 됨 | XML 은 `<eExit>` 인데 enum 은 `eQuit`; `super("File")` 하드코딩 | `rsc/*Config.xml`, `GFileMenu` |
| FIX-12 | 러버밴드로 여러 개 골라도 색이 안 칠해짐 | 마지막 "클릭한" 하나(`selectedShape`) 에만 적용 | `setSelectedShapeFillColor` |
| FIX-13 | 오른쪽→왼쪽으로 그린 사각형/타원은 선택이 안 되고 앵커가 (0,0) 에 뭉침 | `setFrame(ox,oy,x-ox,y-oy)` 가 음수 폭/높이 생성, `Rectangle2D.contains` 는 음수면 false | `GRectangle/GEllipse/GTextArea.dragPoint` 정규화 |
| FIX-14 | 도형을 키우면 앵커도 커지고 회전 핸들이 멀리 날아감 | 앵커 원 자체를 local 에 만들고 AT 로 변환 | `GShape.setAnchors` : 중심점만 변환, 원은 world 고정 크기 |

### 1.4 남아 있는 구조적 문제 (사실) — 이번에 고치지 않고 To-Be 로 남긴 것

1. **`GDrawingPanel` God class** (약 600 LOC): 도형 목록(모델), 선택, undo/redo 스택, 클립보드, 마우스 상태기계, 키 바인딩, 페인팅이 한 클래스에 있다. 단위 테스트가 불가능하고(JPanel 이 필요), 기능 하나 추가할 때마다 이 파일이 커진다.
2. **Undo 가 전체 깊은 복사(Memento) 방식**: 한 번 조작할 때마다 모든 도형을 직렬화→역직렬화한다. 도형 수 n, 조작 수 m 이면 메모리 O(n·m). 간단하지만 큰 문서에서 느려진다. → Command 패턴(각 조작이 `execute/undo` 를 가진 객체) 으로 바꾸면 O(1)/조작.
3. **`GShape.clone()` 이 Java 직렬화**: 느리고, `Shape` 필드가 `Serializable` 로 선언돼 있지 않아 컴파일러 경고가 24개 난다(실제로는 구현 클래스가 직렬화 가능하므로 동작은 한다).
4. **파일 포맷이 Java 직렬화(.gvs)**: 클래스 필드가 바뀌면 이전 파일을 못 연다(`serialVersionUID` 가 전부 1L 이라 조용히 깨질 수 있음). → JSON/XML 로 바꿔야 한다.
5. **`GTransformer` 의 `Graphics2D` 매개변수는 전부 미사용**, `GShape.getSideBounds()` 는 모든 구현이 `null` 을 돌려주는 죽은 추상 메서드, `addPoint` 는 다각형에만 의미가 있다. 인터페이스가 실제 책임보다 넓다(ISP 위반).
6. **`EShapeTool.eSelect` 가 `shapes.GRectangle` 을 가리킴**: "선택" 은 도형이 아닌데 도형 팩토리로 러버밴드를 만든다. 선택 도구는 별도 enum/클래스가 맞다.
7. **연결(wiring) 중복**: `GMainFrame.initialize()` 와 `GPanelManager.associate()` 가 같은 associate 를 두 번 한다. `GPanelManager` 가 만든 `GContainer` 는 아무 데도 안 쓰인다. `GMenuBar.initialize()` 도 fileMenu.associate 를 또 한다.
8. **탭(GBelowPanel) 과 파일 메뉴의 상태 공유 (잠재 버그)**: `GFileMenu.file` 이 하나라서 탭 1 에서 연 파일을 탭 2 에서 Ctrl+S 하면 탭 2 내용으로 덮어쓴다. 문서(탭)마다 파일 경로를 가져야 한다.
9. **`GConstants`**: enum 상수가 XML 로 **가변(mutable static)** 상태가 되고, XML 파싱 코드가 enum 안에 9번 복붙돼 있다. `EEditMenuItem/EMenu/EGraphicsMenuItem` 은 읽기만 하고 쓰는 곳이 없다(편집 메뉴, 선 색/선 스타일/폰트 기능은 미구현). `EHelpEntry` 는 XML 에서 읽지 않는다. `eRectnalge` 오타는 XML 과 맞물려 있어 그대로 두었다.
10. **실행 경로 의존**: XML 을 `src/rsc/koConfig.xml` 상대 경로로 읽어 jar 로 다른 폴더에서 실행하면 설정을 못 읽는다. → `getResourceAsStream` (클래스패스 리소스) 로 바꿔야 한다. `DEFAULT_DIR` 의 Windows 절대경로도 같은 문제.
11. **마우스 모델이 click-move-click**: 일반 그림판의 press-drag-release 와 다르다. 설계 의도(수업 방식)로 보이지만 사용자는 낯설다. 두 방식을 `GTransformer` 레벨에서 선택 가능하게 할 수 있다.
12. **테스트가 없다**. 이번에 만든 회귀 하네스(부록 B)는 저장소 밖(scratch)에 있으며 JUnit 으로 옮겨야 한다.
13. `cropSelectedArea()` 는 버튼만 있고 비어 있다. `GTextArea` 는 폰트/정렬이 없다.

---

## 2. 버그 원인 상세: 로테이션 · 앵커 · 그룹화

### 2.1 직관적 설명

도형마다 "자기 종이(local 좌표계)" 가 있고, 화면은 "책상(world 좌표계)" 이다. `AffineTransform` 은 종이를 책상 위 어디에, 얼마나 돌려서, 얼마나 키워서 놓을지를 적은 메모다.
기존 코드는 **책상 기준으로 잰 중심점**을 **종이 기준 연산**에 넣었다. 종이가 책상 원점에 그대로 놓여 있을 때(아직 이동 전)는 둘이 같아서 잘 되고, 한 번 옮긴 뒤부터 어긋난다. 그래서 "처음엔 되는데 몇 번 만지면 이상해진다" 는 증상이 나온다.

### 2.2 정확한 정의

`AffineTransform at` 에 대해

- `at.rotate(θ, cx, cy)`, `at.translate(dx, dy)`, `at.scale(sx, sy)` 는 **post-concatenate**: `at = at × T`. 새 변환 T 가 **먼저** 적용된다. 즉 T 의 인자는 **local 좌표**다.
- `at.preConcatenate(T)` 는 `at = T × at`. 기존 변환이 먼저, T 가 **나중에(world 에서)** 적용된다. T 의 인자는 **world 좌표**다.

기존 `GRotater.drag`:

```java
Rectangle bounds = shape.getBounds();        // world (변환 적용 후) 바운드
cx = bounds.x + bounds.width / 2;            // world 중심
shape.getAffineTransform().rotate(delta, cx, cy);   // 그러나 local 중심으로 해석됨
```

이동량이 (tx, ty) 라면 실제 회전 중심은 world 에서 (cx + tx, cy + ty) 가 되어 도형이 그 점을 중심으로 **공전**한다. `GResizer` 의 `translate(cx,cy)·scale·translate(-cx,-cy)` 도 같은 이유로 고정점이 (tx, ty) 만큼 어긋나 **점프**한다. `GMover` 의 `at.translate(dx,dy)` 는 local 축 이동이라 **회전한 뒤에는 마우스와 다른 방향**으로 움직인다.

### 2.3 수정 (To-Be)

`GShape` 에 좌표계를 명시한 변환 API 를 두고, 변환기는 그것만 호출한다 (Tell, Don't Ask):

| 메서드 | 좌표계 | 구현 | 사용처 |
|---|---|---|---|
| `translate(dx, dy)` | world | `preConcatenate(T)` | GMover |
| `rotate(θ, cx, cy)` | world 중심 | `preConcatenate(R)` | GRotater |
| `scaleLocal(sx, sy, cx, cy)` | local 고정점 | `translate·scale·translate` (post) | GResizer |
| `toLocal(x, y)` | world→local | `inverseTransform` | GResizer (마우스 변환) |

리사이즈는 **local** 에서 한다. 마우스 좌표를 `toLocal` 로 역변환하고 고정점을 `getLocalBounds()` 로 잡으면, 회전된 도형도 자기 축 방향으로 늘어난다(찌그러지지 않는다). 한 번 scale 을 적용하면 같은 world 점의 local 좌표가 바뀌므로 적용 직후 `px, py` 를 다시 구한다 — 이 한 줄이 없으면 두 번째 드래그부터 튄다.

### 2.4 앵커

앵커는 "선택된 도형의 모서리에 붙은 10px 손잡이" 다. 기존 코드는 손잡이 **원 자체**를 local 좌표에 만들고 그릴 때 AT 로 변환했다. 그래서 (1) 도형을 3배 키우면 손잡이도 30px 가 되고, (2) 회전 핸들의 30px 오프셋도 90px 가 되어 핸들이 도형에서 멀리 떨어진다 (FIX-14). 또 `GGroup` 은 `shape == null` 이라 `setAnchors()` 가 첫 줄에서 return 해 손잡이가 아예 없었다 (FIX-07).

수정: 앵커의 **중심점만** local 바운드에서 구해 AT 로 world 로 옮기고, 원은 world 에 고정 크기로 만든다. 회전 핸들은 도형의 local "위쪽" 벡터를 `deltaTransform` 으로 world 로 옮겨 그 방향으로 30px 띄운다. 결과: 회전된 도형의 손잡이는 회전된 모서리에 붙어 있지만 크기는 항상 같다.

### 2.5 그룹 (Composite)

GoF Composite 의 핵심은 "클라이언트가 잎(leaf)과 합성(composite)을 **구분하지 않고** 같은 인터페이스로 다룬다" 이다. 기존 코드는

- `GMover` 가 `if (shape instanceof GGroup)` 로 자식을 직접 돌았고(클라이언트가 구분함),
- `GResizer/GRotater` 는 그룹의 AT 를 바꿨는데 `GGroup.draw` 는 자식만 그리므로 화면에 아무 변화가 없었다.

수정 후 `GGroup` 은 `translate/rotate/scaleLocal/setFillColor/getLocalBounds/hitBody` 를 override 해 자식에게 전달하고, 자기 AT 는 항상 identity 로 둔다(그룹의 local == world). 그래서 `GResizer` 는 그룹이든 잎이든 같은 코드로 동작하고 `instanceof` 가 사라졌다. `add()` 가 자식의 선택을 해제해 "그룹 안에 자식 앵커" 도 없어졌다.

---

## 3. 객체지향(OOP) 과 Facade 관점 평가

### 3.1 객체지향이란 (직관 → 정의 → 왜 → 예시 → 실무)

- **직관**: 데이터와 그 데이터를 다루는 함수를 한 덩어리(객체)로 묶고, 바깥에서는 "무엇을 해 달라" 고만 말하게 하는 방식. 바깥이 내부 데이터를 직접 만지면 묶은 의미가 없다.
- **정의**: 캡슐화(Encapsulation), 상속(Inheritance), 다형성(Polymorphism), 추상화(Abstraction) 로 프로그램을 **협력하는 객체들의 집합**으로 구성하는 패러다임. 설계 지침으로 SOLID 를 쓴다.
- **왜 필요한가**: 변경을 국소화하기 위해서다. 새 도형을 추가할 때 `GDrawingPanel` 을 건드리지 않아야 하고, 회전 알고리즘을 바꿀 때 `GShape` 내부만 바꾸면 돼야 한다.
- **이 코드에서의 예시**:
  - 잘 된 다형성: `for (GShape s : shapes) s.draw(g)` — 패널은 사각형인지 타원인지 모른다.
  - 깨진 캡슐화: `shape.getAffineTransform().rotate(...)` — 내부 행렬을 꺼내서 바깥(Transformer)이 직접 조작했다. 조작의 "규칙"(어느 좌표계인지)이 바깥에 흩어졌고, 그 규칙을 한 곳(GRotater)이 잘못 알아서 버그가 났다. 수정 후에는 `shape.rotate(θ, cx, cy)` 로 **규칙이 GShape 안으로** 들어갔다.
  - 깨진 다형성: `GMover` 의 `instanceof GGroup`. 수정 후 `GGroup.translate` override 로 해결.
- **실무에서의 활용**: 백엔드에서도 같다. 엔티티의 setter 를 열어 두고 서비스가 필드를 직접 바꾸는 코드(= `getAffineTransform().rotate`) 는 도메인 규칙이 서비스마다 복사되는 원인이 된다. "`order.cancel()` 을 부르지 `order.setStatus(CANCELED)` 를 부르지 말라" 와 같은 원리다.

### 3.2 SOLID 체크 (수정 후 기준)

| 원칙 | 상태 | 근거 |
|---|---|---|
| SRP | ✗ | `GDrawingPanel` 이 모델·히스토리·입력·뷰. `GConstants` 가 상수·설정 파서·팩토리. |
| OCP | △ | 도형 추가는 열려 있다(클래스 + enum + XML). 기능 버튼 추가는 `GFunctionToolBar` 의 switch 와 패널 메서드를 함께 고쳐야 한다. |
| LSP | △ | `GGroup.setPoint/dragPoint` 가 자식에게 전달되는데 "그룹을 그린다" 는 의미가 없다. `getShape()` 가 매번 새 Rectangle 을 돌려주는 것도 잎과 다른 의미다. |
| ISP | ✗ | `getSideBounds()`(전부 null), `addPoint`(다각형만), `GTransformer` 의 `Graphics2D` 인자(전부 미사용). |
| DIP | △ | 툴바가 구체 클래스 `GDrawingPanel` 에 의존. 이번에 추가한 `GRecommendListener`, `GShapeRecognizer` 는 인터페이스 의존으로 만들었다. |

### 3.3 Facade 로 잘 연결되어 있는가

**Facade 의 정의**: 복잡한 서브시스템(여러 클래스) 앞에 단순한 **하나의 진입 인터페이스**를 두어, 클라이언트가 서브시스템 내부 클래스를 몰라도 되게 하는 구조 패턴. 핵심은 "Facade 는 **위임**만 하고 스스로 일하지 않는다" 이다.

**현재 코드**: `GFunctionToolBar → drawingPanel.undo()`, `GColorToolBar → drawingPanel.setSelectedShapeFillColor()`, `GFileMenu → drawingPanel.getShapes()` 처럼 모든 클라이언트가 `GDrawingPanel` 의 public 메서드만 호출한다. 클라이언트가 `GTransformer`, undo 스택, `GShape` 내부를 모른다는 점에서 **Facade 의 효과는 일부 얻고 있다** (사실).

그러나 (의견) 이것을 Facade 라 부르기는 어렵다. 이유:

1. `GDrawingPanel` 이 위임하는 서브시스템이 없다. 도형 목록, 선택, 히스토리, 클립보드가 **패널의 필드**다. Facade 가 아니라 God object 다.
2. 입력(마우스)과 출력(페인팅)이 같은 클래스에 있어서 "GUI 없이 undo 를 테스트" 할 수 없다. Facade 의 장점 중 하나(서브시스템 독립 테스트)가 없다.
3. 연결(wiring)이 `GMainFrame`, `GPanelManager`, `GMenuBar` 세 곳에 중복돼 있어 "누가 조립하는가" 가 불명확하다.

**To-Be (추천안)**:

```
frames.GDrawingPanel (뷰 + 입력 상태기계)  ──▶  editor.GEditor (Facade)
                                                ├─ GDocument   : List<GShape> + 선택 + z-order + Observer(변경 알림)
                                                ├─ GHistory    : Command 스택 (undo/redo)
                                                ├─ GClipboard  : 복사 버퍼
                                                └─ transformer.* (그대로)
툴바/메뉴/추천 패널은 GEditor 만 호출. GDocument 변경 → Observer → 패널 repaint, 추천 패널 갱신.
```

이렇게 나누면 `GEditor` 가 진짜 Facade 가 되고, `GDocument/GHistory` 는 JUnit 으로 GUI 없이 테스트할 수 있다. 이번에 추가한 `GRecommendListener`(Observer) 와 `GShapeRecognizer`(Strategy) 는 이 구조에 그대로 들어맞는다.

---

## 4. FEAT-01: 자유선 → 연관 도형 추천 사이드바

### 4.1 동작

1. 자유선(`GFreeLine`)을 다 그리면 `GDrawingPanel.finishTransform` 이 `GRecommendListener.onStrokeFinished()` 로 알린다 (Observer).
2. `GRecommendPanel` 이 `GStrokeFeatures.of(points)` 로 특징을 뽑는다: 닫힘 여부, RDP 단순화 꼭짓점 수, 직선도, 원형도(4πA/P²), 사각도(넓이/바운딩박스), 종횡비.
3. `GGeometricRecognizer`(로컬 휴리스틱)가 즉시 5개 후보에 0~1 점수를 매겨 보여 준다.
4. 체크박스가 켜져 있으면 `GOllamaRecognizer` 가 **로컬 LLM**(Ollama, `http://localhost:11434/api/generate`) 에 특징을 텍스트로 보내 순위를 받아(JSON) 기하 점수와 0.4:0.6 으로 합산해 다시 보여 준다. SwingWorker 로 백그라운드에서 돌고, Ollama 가 꺼져 있으면 상태줄에만 알리고 기하 결과를 유지한다(graceful degradation).
5. 항목을 누르면 `GDrawingPanel.replaceShape()` 가 자유선을 같은 z-order 자리에서 깔끔한 도형으로 바꾼다. Undo 된다.

### 4.2 설계 포인트

- **Strategy**: `GShapeRecognizer` 인터페이스에 두 구현. 나중에 비전 모델(llava 에 PNG 전송), $1 recognizer, 학습 모델을 추가해도 패널은 안 바뀐다.
- **Observer**: 패널(frames)은 `recommend.GRecommendListener` 만 안다. `recommend` 패키지는 `frames` 를 모른다 (의존 방향 단일화).
- **의존성 0**: HTTP 는 `java.net.http.HttpClient`, JSON 은 직접 쓴 120줄 파서(`GJson`). 프로젝트가 빌드 도구 없는 Eclipse 프로젝트라 외부 jar 를 넣지 않았다.
- **EDT 규칙**: 네트워크는 워커 스레드, UI 갱신은 `done()`(EDT). 요청 순번(`requestSeq`)으로 늦게 도착한 옛 응답을 무시한다.
- **설정**: `GConstants.GRecommender` — `-Dpaint.ollama.model=...` 시스템 속성 > XML `<GRecommender>` > 기본값(llama3.2).

### 4.3 LLM 을 어디에 쓰는 게 맞는가 (의견)

좌표 몇 개로 "사각형인지 원인지" 를 맞히는 일은 LLM 보다 기하 휴리스틱이 더 정확하고 빠르다. 그래서 LLM 은 **재순위(re-ranker)** 와 **애매한 경우의 타이브레이커**(오각형 vs 타원)로만 쓰고, 항상 휴리스틱이 먼저 답을 낸다. 포트폴리오에서는 "LLM 을 썼다" 보다 "**LLM 이 필요한 지점을 좁혀서 썼고 없어도 동작한다**" 는 판단을 강조하는 편이 낫다. 정확도를 올리려면 텍스트 좌표가 아니라 **그려진 PNG 를 비전 모델(llava 등)에 보내는** 것이 다음 단계다 (Ollama `images` 필드).

---

## 5. 과제 평가와 포트폴리오 전략

### 5.1 과제로서의 평가 (의견, 근거 포함)

| 관점 | 평가 | 근거 |
|---|---|---|
| 기능 범위 | 충분 | 7종 도형, 선택/이동/리사이즈/회전, 그룹, undo/redo, 복사/붙이기, z-order, 저장/열기/인쇄, 3개 언어 |
| 패턴 적용 "의도" | 좋음 | 도형 계층, Transformer 분리, 설정 외부화, Composite 시도 |
| 패턴 적용 "완성도" | 부족 | Composite 반쪽(instanceof), Facade 아님(God class), 캡슐화 누수로 핵심 버그 |
| 정확성 | 부족했음 → 수정 | 회전/앵커/그룹/undo/더블클릭/선 선택 등 사용자가 바로 느끼는 버그 14건 |
| 코드 품질 | 보통 | 중복(앵커 그리기 3벌), 죽은 코드(movePoint, getSideBounds), 디버그 print, 오타(eRectnalge) |
| 테스트 | 없음 | — |

### 5.2 포트폴리오에서 강조할 것 (추천 순서)

1. **버그를 "원인" 까지 추적한 이야기 (FIX-01)**. 증상(공전) → 가설 → `AffineTransform` 의 pre/post-concatenate 와 좌표계 → 수정 → 회귀 테스트. 선형대수·컴퓨터그래픽스·캡슐화가 한 사례에 들어 있다. 면접에서 10분짜리 설명이 된다.
2. **Composite 를 제대로 완성한 과정 (FIX-07)**. "instanceof 를 없애기 위해 무엇을 어디로 옮겼는가" 는 객체지향 이해를 보여 주는 가장 짧은 예시다.
3. **LLM 기능의 "설계"** (4.2, 4.3). Strategy/Observer, 백그라운드 스레드와 EDT, graceful degradation, 외부 의존성 없이 HTTP/JSON 을 직접 다룬 점. 금융 IT/백엔드 지원이라면 "외부 서비스가 죽어도 핵심 기능은 산다" 는 설계를 강조하라.
4. **테스트 가능한 구조로 가는 로드맵** (3.3 To-Be). 지금은 Robot+Xvfb 하네스로 검증했고, 다음 단계가 `GEditor/GDocument` 분리 + JUnit 이라는 것을 문서로 보여 주면 "리팩터링을 계획할 줄 안다" 가 전달된다.
5. 설정 외부화(i18n) 는 이미 있으니 한 줄로만.

강조하지 말아야 할 것: 기능 개수, "Swing 으로 그림판을 만들었다" 자체, 패턴 이름 나열.

### 5.3 다음 작업 (우선순위)

| 순위 | 작업 | 이유 |
|---|---|---|
| P1 | Gradle 빌드 + JUnit 5 + 부록 B 하네스 이식 + GitHub Actions(xvfb) | 지금부터의 모든 리팩터링을 안전하게 만든다 |
| P2 | `GEditor/GDocument/GHistory` 분리 (3.3) | God class 해소, 진짜 Facade |
| P3 | Command 패턴 undo | 스냅샷 복사 제거 |
| P4 | 파일 포맷 JSON (직렬화 제거), XML 을 클래스패스 리소스로 | 배포 가능한 jar |
| P5 | 선 색/선 굵기/폰트 (XML 에 이미 라벨 있음), crop 구현, 탭별 파일 경로 | 미완 기능 정리 |
| P6 | 비전 모델 기반 인식 + 정확도 비교 표 | FEAT-01 심화 |

---

## 부록 A. 수정 지점 색인 (코드 주석 ↔ 문서)

모든 주석은 `[2026-10-18 수정자: Jung Minkyu] <ID>` 형식이며 `As-Is / To-Be / 연관` 을 갖는다.

| ID | 파일 |
|---|---|
| FIX-01 | `shapes/GShape.java` (변환 API, getLocalBounds, toLocal), `transformer/GMover.java`, `transformer/GRotater.java`, `transformer/GResizer.java`, `shapes/GGroup.java` |
| FIX-02 | `frames/GDrawingPanel.java` (rubberBand, startTransform, finishTransform, paintComponent), `transformer/GMover.java` (다중 선택 생성자) |
| FIX-03 | `frames/GDrawingPanel.java` (saveStateForUndo 와 모든 호출처) |
| FIX-04 | `frames/GDrawingPanel.java` (MouseEventHandler.mouse2Clicked) |
| FIX-05 | `frames/GDrawingPanel.java` (onShape) |
| FIX-06 | `shapes/GShape.java` (hitBody), `shapes/GLine.java`, `shapes/GFreeLine.java`, `shapes/GTextArea.java` |
| FIX-07 | `shapes/GGroup.java`, `frames/GDrawingPanel.java` (groupSelectedShapes/ungroupSelectedShapes) |
| FIX-08 | `frames/GDrawingPanel.java` (selectedShapes 제거) |
| FIX-09 | `shapes/GTextArea.java` |
| FIX-10 | `transformer/GResizer.java` (MIN_SIZE) |
| FIX-11 | `rsc/koConfig.xml`, `rsc/enConfig.xml`, `rsc/jpConfig.xml`, `menus/GFileMenu.java` |
| FIX-12 | `frames/GDrawingPanel.java` (setSelectedShapeFillColor) |
| FIX-13 | `shapes/GRectangle.java`, `shapes/GEllipse.java`, `shapes/GTextArea.java` (dragPoint) |
| FIX-14 | `shapes/GShape.java` (setAnchors, drawAnchors, contains, hitTestAnchor) |
| FEAT-01 | `recommend/*` (GRecommendListener, GStrokeFeatures, GShapeRecognizer, GGeometricRecognizer, GOllamaRecognizer, GJson, ESuggestShape, GShapeSuggestion), `frames/GRecommendPanel.java`, `frames/GDrawingPanel.java` (setRecommendListener, replaceShape), `frames/GMainFrame.java`, `manager/GPanelManager.java`, `global/GConstants.java` (ERecommend, GRecommender), `rsc/*Config.xml`, `shapes/GFreeLine.getPoints()`, `shapes/GPolygon.setVertices()` |

## 부록 B. 검증 방법

- 컴파일: `javac -encoding UTF-8 -d bin $(find src -name '*.java')` — 오류 0.
- 헤드리스 하네스(저장소 밖, scratch): 변환 수학(이동 후 회전 중심, 리사이즈 고정점, 0 나눗셈, 역방향 드래그), 그룹 전달, 앵커 고정 크기, 인식기(정사각형/원/직선/삼각형/오각형/손떨림 포함), JSON 파서, Ollama 응답 파싱, 모의 Ollama HTTP 서버 — 전부 통과.
- GUI 테스트: Xvfb 위에서 `java.awt.Robot` 으로 실제 마우스 이벤트를 넣어 그리기/undo/이동/리사이즈/회전/더블클릭/추천 적용/그룹/겹침 클릭/선 클릭/복사-붙이기 시나리오 실행, EDT 예외 0 확인.
- Ollama 실제 모델 응답은 이 환경에 Ollama 가 없어 **모의 서버로만** 검증했다. 실제 모델에서 프롬프트 품질은 사용자가 확인해야 한다.

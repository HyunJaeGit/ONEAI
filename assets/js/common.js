'use strict';
(() => {
const root = document.body.dataset.root || '';
const group = document.body.dataset.group;
const page = document.body.dataset.page;
// 별도 아이콘 파일 요청으로 발생하는 favicon.ico 404를 방지합니다.
const icon = document.createElement('link');
icon.rel = 'icon';
icon.href = 'data:,';
document.head.append(icon);
const sections = {"business":{"label":"양품화 사업 안내","pages":[["overview","사업 개요·전체 흐름"],["collection","수거 대상·품목 기준"],["inspection","현장 점검·판정"],["distribution","양품화·관리전환·배부"]],"references":[["pencil","펜슬 취급·제공 안내"]]},"school":{"label":"학교 측 업무 프로세스","pages":[["preparation","수거 전 준비"],["os-preparation","OS별 준비"],["schedule","일정 확인·변경"],["collection-day","수거 당일 대응"],["receipt","수거확인서"],["transfer","명렬표·관리전환"],["installation","배부·설치 확인"]]},"relocation":{"label":"디벗 재배치·여분 기기","pages":[["index","재배치·여분 기기 회수"]]}};
const current = key => group === key ? ' aria-current="true"' : '';
// 공통 화면은 이 파일에서만 관리합니다. HTML에는 렌더링할 자리만 둡니다.
const siteName = '디벗 양품화 메뉴얼';
const bindDialog = (dialog, trigger, closeSelector, contentSelector) => {
  trigger.addEventListener('click', () => {
    setMenuOpen(false);
    dialog.showModal();
    dialog.querySelector(contentSelector).scrollTop = 0;
    document.body.classList.add('patch-notes-open');
  });
  dialog.querySelector(closeSelector).addEventListener('click', () => dialog.close());
  dialog.addEventListener('click', event => {
    const bounds = dialog.getBoundingClientRect();
    if (event.target === dialog && (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)) dialog.close();
  });
  dialog.addEventListener('close', () => {
    if (document.querySelector('dialog[open]')) return;
    document.body.classList.remove('patch-notes-open');
    menuButton.focus();
  });
};
document.getElementById('site-header').innerHTML = `<div class="container"><div class="header-top"><a class="brand" href="${root || './'}">${siteName.replace(' 메뉴얼', ' <span>메뉴얼</span>')}</a><button type="button" class="print-button" aria-label="현재 페이지 인쇄" title="현재 페이지 인쇄"><svg aria-hidden="true" viewBox="0 0 24 24" focusable="false"><path d="M7 8V3h10v5M7 17H5a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-2M7 14h10v7H7z"/></svg></button></div><nav class="main-nav" aria-label="주 메뉴"><a href="${root || './'}"${group === 'home' ? ' aria-current="page"' : ''}>홈</a><a href="${root}pages/business/"${current('business')}>양품화 사업 안내</a><a href="${root}pages/school/"${current('school')}>학교 측 업무 프로세스</a><a href="${root}pages/contact.html"${current('contact')}>문의 경로</a><a class="nav-secondary" href="${root}pages/relocation/"${current('relocation')}>디벗 재배치·여분 기기</a></nav></div>`;
// 루트 패치내역.md 변경 시 아래 날짜별 목록도 함께 갱신합니다.
const patchNotes = [{"title":"2026-10-26","items":["공통 메뉴에서 로컬 개발자용 이벤트 조회 화면으로 이동할 수 있는 링크 추가."]},{"title":"2026-10-23","items":["헤더·푸터·상세 목차의 중복 코드를 정리하고 공통 JS에서 관리하도록 통합.","패치노트·문의하기 팝업의 열기·닫기 처리를 공통 함수로 통합.","양품화 공문 재발송 문의처로 교육청 창의미래교육과 안내 추가.","양품화 기기 수량 변경 요청은 서울시교육청 창의미래교육과로 문의하도록 안내 추가.","명렬표를 이메일로 이미 제출한 경우 변경 수량에 맞춰 수정 후 재발송하도록 안내 추가.","추가 안내를 문의 경로·명렬표 페이지 및 전체 검색에 반영."]},{"title":"2026-10-22","items":["수거 일정 변경·승인 후 조정 및 수거 3일 전 담당자 연락 안내 추가.","학생 과실 파손·분실 본인부담 기준 추가.","양품화 시스템과 TGS·KT 보급사업 사이트 구분 안내 추가.","삼성 크롬북 하단 고무 루버·키보드 부품 서비스 안내 추가.","펜슬 상세 페이지 신설: OS별 제공 기준·배터리 교환·양품화 전후 제공 제품 정리.","일반 절차 우선 배치, 예외·한시사업은 접기 안내로 분리.","목차·검색·상세 링크·인쇄 동작 갱신, 최종 배포일 수정.","인쇄 버튼 옆 메뉴에 날짜별 패치노트 모달 추가.","메뉴에 내용추가신청 추가: 이메일 작성·이메일 주소 및 신청 내용 복사 지원."]},{"title":"2026-10-21 · 최초 구축","items":["프로젝트 첫 커밋 : 정적 매뉴얼 사이트 구축 및 배포.","양품화 사업 안내·학교 업무 7단계·재배치·문의 페이지 구성.","제공 자료 기반 수거·준비·관리전환·배부 절차 정리.","공통 메뉴·모바일 화면·검색 및 검색어 강조·인쇄 기능 적용.","GitHub Pages 배포 설정 및 공통 푸터 정보 추가."]}];
const headerTop = document.querySelector('.header-top');
const headerActions = document.createElement('div');
headerActions.className = 'header-actions';
headerActions.append(headerTop.querySelector('.print-button'));
const headerMenu = document.createElement('div');
headerMenu.className = 'header-menu';
headerMenu.innerHTML = '<button type="button" class="header-menu-button" aria-label="메뉴" aria-expanded="false" aria-controls="header-menu-panel" title="메뉴"><svg aria-hidden="true" viewBox="0 0 24 24" focusable="false"><path d="M4 6h16M4 12h16M4 18h16"/></svg></button><div id="header-menu-panel" class="header-menu-panel" hidden><button type="button" class="patch-notes-trigger" aria-haspopup="dialog" aria-controls="patch-notes-dialog">패치노트</button></div>';
headerActions.append(headerMenu);
headerTop.append(headerActions);
const menuButton = headerMenu.querySelector('.header-menu-button');
const menuPanel = headerMenu.querySelector('.header-menu-panel');
const developerEventsLink = document.createElement('a');
developerEventsLink.className = 'developer-events-link';
developerEventsLink.href = '/dev/events';
developerEventsLink.textContent = '수집 이벤트 조회';
menuPanel.prepend(developerEventsLink);
const requestTrigger = document.createElement('button');
requestTrigger.type = 'button';
requestTrigger.className = 'content-request-trigger';
requestTrigger.textContent = '문의하기';
requestTrigger.setAttribute('aria-haspopup', 'dialog');
requestTrigger.setAttribute('aria-controls', 'content-request-dialog');
menuPanel.append(requestTrigger);
const setMenuOpen = open => {
  menuButton.setAttribute('aria-expanded', String(open));
  menuPanel.hidden = !open;
};
menuButton.addEventListener('click', () => setMenuOpen(menuPanel.hidden));
document.addEventListener('pointerdown', event => {
  if (!headerMenu.contains(event.target)) setMenuOpen(false);
});
document.addEventListener('focusin', event => {
  if (!headerMenu.contains(event.target)) setMenuOpen(false);
});
headerMenu.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !menuPanel.hidden) {
    event.preventDefault();
    event.stopPropagation();
    setMenuOpen(false);
    menuButton.focus();
  }
});
const patchDialog = document.createElement('dialog');
patchDialog.id = 'patch-notes-dialog';
patchDialog.className = 'patch-notes-dialog';
patchDialog.setAttribute('aria-labelledby', 'patch-notes-title');
patchDialog.innerHTML = '<div class="patch-notes-heading"><h2 id="patch-notes-title">패치노트</h2><button type="button" class="patch-notes-close" aria-label="패치노트 닫기" autofocus>닫기</button></div><div class="patch-notes-content"></div>';
const patchContent = patchDialog.querySelector('.patch-notes-content');
patchNotes.forEach(note => {
  const section = document.createElement('section');
  const title = document.createElement('h3');
  title.textContent = note.title;
  const list = document.createElement('ul');
  note.items.forEach(text => {
    const item = document.createElement('li');
    item.textContent = text;
    list.append(item);
  });
  section.append(title, list);
  patchContent.append(section);
});
document.body.append(patchDialog);
bindDialog(patchDialog, headerMenu.querySelector('.patch-notes-trigger'), '.patch-notes-close', '.patch-notes-content');
const requestDialog = document.createElement('dialog');
requestDialog.id = 'content-request-dialog';
requestDialog.className = 'patch-notes-dialog content-request-dialog';
requestDialog.setAttribute('aria-labelledby', 'content-request-title');
requestDialog.innerHTML = `<div class="patch-notes-heading"><h2 id="content-request-title">문의하기</h2><button type="button" class="request-close" aria-label="문의하기 닫기" autofocus>닫기</button></div><div class="request-content"><p class="manual-note">매뉴얼 내용 추가·수정 및 기능 제안을 Google Forms로 접수합니다.</p><p class="request-actions"><a class="request-form-link" href="https://forms.gle/JL4TDgPM6Z9VQfDT7" target="_blank" rel="noopener noreferrer">신청 양식 열기</a></p><p class="manual-note">양식이 열리지 않으면 링크 접근 권한을 확인해 주세요.</p></div>`;
document.body.append(requestDialog);
bindDialog(requestDialog, requestTrigger, '.request-close', '.request-content');
const transitionKey = 'nav-page-transition';
try {
  const pendingTransition = JSON.parse(sessionStorage.getItem(transitionKey) || 'null');
  sessionStorage.removeItem(transitionKey);
  if (pendingTransition && pendingTransition.url === location.href && Date.now() - pendingTransition.at < 2500) {
    document.body.classList.add('nav-page-entering');
    window.setTimeout(() => document.body.classList.remove('nav-page-entering'), 280);
  }
} catch (_) {}
document.querySelectorAll('.main-nav a, .header-top .brand').forEach(link => {
  link.addEventListener('click', event => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const destination = new URL(link.href, location.href);
    if (destination.origin !== location.origin || destination.pathname === location.pathname || destination.hash) return;
    event.preventDefault();
    if (document.body.classList.contains('nav-page-leaving')) return;
    try { sessionStorage.setItem(transitionKey, JSON.stringify({ url:destination.href, at:Date.now() })); } catch (_) {}
    document.body.classList.add('nav-page-leaving');
    window.setTimeout(() => location.assign(destination.href), 160);
  });
});
const menu = document.getElementById('section-menu');
if (menu && sections[group]) {
const section = sections[group];
menu.innerHTML = `<details class="section-menu-disclosure" open><summary>${section.label} 목차</summary><ul>${section.pages.map(([slug, title]) => `<li><a href="${slug === 'index' ? './' : `${slug}.html`}"${page === slug ? ' aria-current="page"' : ''}>${title}</a></li>`).join('')}</ul>${section.references ? `<h3 class="section-menu-reference-title">품목별 상세 안내</h3><ul>${section.references.map(([slug, title]) => `<li><a href="${slug === 'index' ? './' : `${slug}.html`}"${page === slug ? ' aria-current="page"' : ''}>${title}</a></li>`).join('')}</ul>` : ''}</details>`;
const disclosure = menu.querySelector('details');
const mobile = window.matchMedia('(max-width:720px)');
const syncMenu = () => { disclosure.open = !mobile.matches; };
syncMenu();
mobile.addEventListener('change', syncMenu);
}
document.getElementById('site-footer').innerHTML = `<div class="container"><p><strong>${siteName}</strong> · 사내 참고용</p><p>최종 배포일: 2026년 10월 22일</p><p>개발 및 배포: 권현재 · fatking25@kakao.com</p></div>`;

/* 공개 HTML 본문만 수록한 검색 색인. 본문 수정 시 이 목록도 갱신합니다. */
const searchPages = [{"path":"./","title":"홈","text":"양품화 사업 안내 사업 개요·전체 흐름 수거 대상·품목 기준 현장 점검·판정 양품화·관리전환·배부 학교 측 업무 프로세스 01 수거 전 준비 02 OS별 준비 03 일정 확인·변경 04 수거 당일 대응 05 수거확인서 06 명렬표·관리전환 07 배부·설치 확인"},{"path":"pages/business/collection.html","title":"수거 대상·품목 기준","text":"홈 / 양품화 사업 안내 / 수거 대상·품목 기준 양품화 사업 안내 전체 목록 양품화 사업 안내 수거 대상·품목 기준 기기 본체 수거 기준 반납 점검, 계정 로그아웃, 차단 해제를 마친 본체를 수거합니다. 파손·불량 기기는 A/S 완료 후 수거합니다. 화면이나 기본 동작에 이상이 있거나, 충전되지 않아 점검할 수 없는 기기는 수거하지 않습니다. 케이스가 파손되거나 없어도 본체 수거를 제한하지 않습니다. 당일 발견 불량 등은 아래 추가 확인에서 확인하세요. 부속품·케이스 처리 기준 부속품 및 케이스 처리 기준 품목 처리 전용펜 양품화 때 수거하지 않으며, 현장에서 상태를 점검해 정상·비정상을 표시합니다. 펜슬 취급·제공 안내 충전기·케이블 학교에 보관했다가 신입생에게 지급합니다. 케이스 양품화 과정에서 기존 케이스를 새 케이스로 교체합니다. 화면 보호필름 양품화 과정에서 새 보호필름을 부착합니다. 기기 구분 시 확인할 정보 기기의 보급 연도·상하반기, 라벨·각인, 제품명을 확인합니다. 2024년 상반기 기기는 KT(TGS), 하반기 기기는 LG헬로비전(케이원) 관리로 구분합니다. A/S 문의 전 기기 라벨을 확인하세요. A/S 문의 경로 확인 보급 연도·사업자·제품명 구분 보급 연도와 라벨·각인의 표시로 관리 사업자를 구분합니다. 보급 연도별 관리 사업자 보급 연도 관리 사업자 2022년 케이원 2023년 TGS 2024년 상반기 TGS (KT 보급) 2024년 하반기 케이원 (LG헬로비전 보급) 2025년 케이원 2026년 TGS 2024년 보급 단말 제품명 보급 구분 안드로이드 아이패드 윈도우 크롬북 웨일북 2024년 상반기 SM-X610 iPad 9th NT520QFD XQ520QEA 14TN30T 2024년 하반기 SM-X610 iPad 10th NT520QFD XQ520QEA 14TN30S 위 표는 2024년 보급 단말의 제품명입니다. 이후 보급 단말이나 현재 학교 보유 목록에 그대로 적용하지 말고, 실제 기기 라벨과 학교 보유 현황으로 확인하세요. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 예외 · 수거 당일 현장에서 수리 필요 기기를 발견한 경우 수리동의서 제출 여부를 확인한 뒤, 제출된 기기는 현장 수거해 바로 양품화를 진행합니다. 수리동의서 접수·확인 방법 예외 · 기존 케이스를 학교 예비용으로 보관하려는 경우 재활용 가능한 케이스를 학교가 예비용으로 원하면 수거하지 않을 수 있습니다. 한시적 별도 사업 · 애플 1세대 펜슬 배터리 불량 일반 전용펜 취급과 구분되는 한시적 사업입니다. 대상·보고·반납·교환 수량은 펜슬 배터리 불량 교환 안내 에서 확인하세요. 삼성 크롬북 한정 · 하단 고무 루버·키보드 부품 다음은 노트북 형태의 삼성 크롬북에 한정된 안내입니다. 삼성 크롬북 하단 고무 루버(하단 미끄럼방지용 부속품)는 양품화 수거 대상 부품이 아니며, 없어도 기기 본체는 양품화 수거가 가능합니다. 양품화 과정에서 루버를 새로 부착하거나 새 부품을 지급하지 않습니다. 삼성 크롬북 고무 루버 및 키보드 부품 처리 기준 구분 안내 내용 루버 보충·교체 필요하면 별도로 서비스를 신청합니다. 학교 행정실을 통해 한 번에 20~30개씩 구매하는 사례도 있습니다. 삼성 크롬북 루버 가격 1개당 1,000원, 4개 기준 총 4,000원입니다. 학생이 직접 구매해도 같은 가격입니다. 가격 정보 전달일: 2026년 10월 22일. 구매 시 부품 구분 프론트(앞쪽)와 리어(뒤쪽)의 루버 부품이 다릅니다. 4개를 모두 같은 종류로 구매하지 않도록 필요한 위치별 부품을 확인합니다. 키보드 고무 마킹 등 부품 수리·교체가 필요하면 A/S를 요청해 서비스를 받도록 안내합니다. 기기 보급 연도·관리 사업자별 A/S 문의 경로 를 확인하세요. 이전: 사업 개요·전체 흐름 전체 목록 다음: 현장 점검·판정"},{"path":"pages/business/distribution.html","title":"양품화·관리전환·배부","text":"홈 / 양품화 사업 안내 / 양품화·관리전환·배부 양품화 사업 안내 전체 목록 양품화 사업 안내 양품화·관리전환·배부 양품화 작업 클리닝: 기존 라벨·스티커 잔여물·오염 제거 기기별 라벨·QR 부착과 공정 정보 관리 보호필름·케이스 교체 OS별 MDM·SENEDU 설정과 학교급에 맞는 앱 설치 관리전환 2월 반 배정과 학교별 수량 확정 이후 대상 기기의 학교정보를 변경합니다. 학생 명렬표를 현행화하고 OS에 따라 MDM·ASM·CEU·웨일북 관리 기기 등록 학교를 전환합니다. 크롬북·웨일북의 관리전환은 양품화 사업자가 진행합니다. 학교의 명렬표·관리전환 준비 배부·설치 확인 담당 매니저가 학교와 사전 협의해 방문 일정을 확정하며, 정확한 일정은 학교 일정을 고려해 교육청 공문으로 안내됩니다. 공문 및 디벗관리시스템에서 일정을 확인합니다. 기기·구성품 수량과 설정을 검사한 후 설치확인서에 서명합니다. 전용펜과 충전기는 양품화 대상이 아닙니다. 신규 지급 구성품은 펜촉·펜슬 캡·펜슬이며, 펜슬 제공 수량·제품은 펜슬 취급·제공 안내 에서 확인합니다. 그 밖의 품목은 부족하고 교체가 필요하면 구매합니다. OS별 구성품은 배부·설치 확인 에서 확인합니다. 최종 일정·수량 확인 학교별 배부 일정은 담당 매니저와 사전 협의 후 공문으로 확인하고, 최종 수량은 공문 및 디벗관리시스템에서 확인합니다. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 학교 자체 스티커를 보존하려는 경우 학교 자체 부착 스티커의 제거를 원하지 않으면 담당자에게 알립니다. 한시적 별도 사업 · 애플 정품펜슬 배터리 불량 교환 대상·조건과 일반 제공분 합산 기준은 펜슬 안내의 한시적 교환사업 에서 확인하세요. 이전: 현장 점검·판정 전체 목록"},{"path":"pages/business/","title":"양품화 사업 안내","text":"홈 / 양품화 사업 안내 사업 이해 양품화 사업 안내 사업 안내 사업 개요·전체 흐름 수거 대상·품목 기준 현장 점검·판정 양품화·관리전환·배부 품목별 상세 안내 펜슬 취급·제공 안내"},{"path":"pages/business/inspection.html","title":"현장 점검·판정","text":"홈 / 양품화 사업 안내 / 현장 점검·판정 양품화 사업 안내 전체 목록 양품화 사업 안내 현장 점검·판정 현장 점검 순서 기기 보관 위치와 충전 상태를 확인합니다. 충전된 모든 기기의 전원을 켜 외관과 기본동작을 점검합니다. 디스플레이·케이스·스크래치·키보드 등 외관을 점검합니다. 전원·부팅·계정 및 잠금 해제 등 기본동작을 점검합니다. 수거·미수거·재방문 여부와 특이사항을 점검 확인서에 기록합니다. 판정 기준 상태별 현장 판단 상태 기준 및 확인 사항 찍힘·스크래치·스티커 흔적·오염 사용에 문제가 없는 외관 흔적은 수리 없이 수거·양품화할 수 있습니다. 화면 깨짐·줄·번인·불량화소 일반 수리 필요 기기는 수거 전에 A/S를 진행합니다. 전원·부팅·버튼·카메라 불량 일반 수리 필요 기기는 수거 전에 A/S를 진행합니다. 파손 케이스 케이스 상태를 확인한 뒤 기준에 따라 양품화 대상 여부를 판정합니다. 전용펜·충전기 양품화 대상이 아닙니다. 계정·잠금 미해제 수거 전에 계정 로그아웃·잠금 해제를 완료합니다. 미해제 기기는 아래 OS별 추가 확인을 확인하세요. 수거 후 상세점검 상세점검 내역은 회수 후 15일 이내 디벗관리시스템에 등록합니다. 가벼운 찍힘·스크래치는 수리 없이 양품화하고, 양품화가 불가능한 불량은 A/S 수리 후 진행합니다. 수거 전 필수 확인 미충전 기기는 현장에서 점검할 수 없어 수거하지 않습니다. 충전 후 2차 방문해 기본점검을 마친 뒤 수거합니다. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 예외 · 수거 당일 발견한 수리 필요 기기 수리동의서 제출 여부를 확인하고, 제출된 기기는 현장 수거 후 바로 양품화를 진행합니다. 수리동의서 신청·확인 OS별 확인 · 계정·잠금 미해제 기기 일부 OS는 계정·잠금이 해제되지 않은 기기를 수거한 뒤 A/S센터에서 초기화해 작업장으로 이동할 수 있습니다. 안드로이드·iOS는 이 절차에서 제외되므로, 수거 전에 계정·잠금을 해제하세요. 그 밖의 OS는 방문 전 담당자에게 적용 가능 여부를 확인하세요. 삼성 크롬북 한정 · 하단 고무 루버가 없는 경우 하단 고무 루버(하단 미끄럼방지용 부속품)가 없어도 본체는 수거 가능합니다. 루버는 양품화 수거·신규 부착·새 부품 지급 대상이 아니며, 필요하면 별도로 서비스를 신청합니다. 삼성 크롬북 루버 구매·부품 구분 안내 비용 문의 시 · 학생 과실 파손·분실 본인부담 파손·분실 본인부담 기준 구분 본인부담 기준 학생 과실로 인한 파손 본인부담률은 20%이며, 본인부담금은 최대 4만 원입니다. 기기 분실 본인부담률은 90%입니다. 확인 필요 분실 시 부담률 90%를 적용하는 기준 금액은 확인이 필요합니다. 실제 부담금은 비용·기기 고장·분실 문의 경로 로 확인하세요. 이전: 수거 대상·품목 기준 전체 목록 다음: 양품화·관리전환·배부"},{"path":"pages/business/overview.html","title":"사업 개요·전체 흐름","text":"홈 / 양품화 사업 안내 / 사업 개요·전체 흐름 양품화 사업 안내 전체 목록 양품화 사업 안내 사업 개요·전체 흐름 사업 개요 졸업생이 약 3년간 사용한 디벗을 회수하여 다음 학생이 사용할 수 있도록 재정비한 뒤 학교에 다시 배부하는 사업입니다. 내용연수 6년과 중·고등학교 3년 학제를 고려해, 사용자 변경 시점에 기기 상태를 보완하고 소프트웨어를 다시 설정합니다. 전체 흐름 반납 준비: 반납 점검표 수합·점검, 계정 로그아웃, 차단 정책 해제 수거: 학교 현장 1차 점검 후 수거 여부 판정 상세점검: 기기별 외관·기능 상세점검 양품화: 클리닝, 라벨링, 보호필름·케이스 교체, 소프트웨어 설정 관리전환: 확정 수량에 맞춰 기기의 소속 학교 정보 변경 학교 배부: 신입생 대상 배송·설치·검수 일정과 진행 확인 사업 계획은 수거 2026년 10~12월, 학교 배부 2027년 2~3월 입니다. 수거 기간은 10월 15일~12월 24일, 학교별 방학일 이전으로 안내합니다. 개별 학교 방문일을 의미하지 않으므로 일정 확인·변경 에서 확정 상태를 확인하세요. 디벗관리시스템 [사업관리]에서 대상·입고·양품화 완료 수량과 공정별 진척률을 조회할 수 있습니다. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 확인 필요 · 자료의 공문 안내 시점이 다른 경우 확인 필요 공문 안내 시점 표기가 다릅니다. 학교에 도착한 공문과 시스템의 최종 확정일을 기준으로 확인하세요. 전체 목록 다음: 수거 대상·품목 기준"},{"path":"pages/contact.html","title":"문의 경로","text":"홈 / 문의 경로 문의 경로 문의 유형별 경로 문의 유형 경로 양품화 일정·수거·배부 1566-6647 → 연결 후 3번 · 평일 09:00~18:00 양품화 공문 재발송 교육청의 창의미래교육과로 문의해 주세요. 양품화 기기 수량 변경 요청 서울시교육청 창의미래교육과로 문의해 주세요. 수거 시기의 양품화 대상 수량 명렬표를 이메일로 이미 보낸 경우에는 변경된 수량에 맞춰 수정한 후 kone_sen@k-one.co.kr 로 다시 보내 주세요. 2월 신입생 수 확정 후 명렬표와는 다른 자료입니다. 수량 변경 시 명렬표 안내 2024년 하반기 보급 단말 A/S 1566-6647 → 연결 후 1번 · 평일 08:00~18:00 LG헬로비전(케이원) 수행 라벨 확인 2024년 상반기 보급 단말 A/S 1566-6647 → 연결 후 2번 · 평일 08:00~18:00 KT컨소시엄(TGS) 수행 라벨 확인 관리시스템 계정·로그인 1566-6647 내선 1번 또는 학교 업무용 이메일 계정에서 kone_sen@k-one.co.kr로 문의 일정 확인·변경 디벗관리시스템 의 회수일정·배송·설치일정에서 확인·신청. 수거 희망 일정 변경 방법 센클라우드 오픈채팅 업무포털 → 센클라우드 → 채팅 → 오픈채팅에서 ‘2027학년도 디벗 양품화’ 검색 후 학교급 선택 문의 전 확인 일정은 시스템에 표시된 날짜·시간·수량·상태를 확인합니다. 수거 누락·분실·파손 문의는 기기 식별정보와 수거·인계 기록을 준비합니다. 아이디·비밀번호 문의에는 학교 업무용 이메일 계정을 사용합니다. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 접속 문제 · 검색으로 찾은 디벗관리시스템이 다른 경우 인터넷에서 ‘디벗관리시스템’을 검색해 나오는 사이트와 양품화 업무에 사용하는 시스템은 다릅니다. 아래 시스템 구분은 2026년 10월 22일 기준입니다. 디벗관리시스템별 용도와 접속 방법 구분 대상·용도 접속 안내 양품화 업무용 디벗관리시스템 학교의 2027년 양품화 일정 확인·변경, 수거확인서, 명렬표 등 업무 dbs.edu-portal.ai 학교 관리용 비공개 시스템이므로 인터넷 검색으로 찾을 수 없습니다. 학교에 안내된 주소로 접속하세요. TGS·KT 측 보급사업 관리 사이트 2026년 6차 보급사업 때 운영한 사이트입니다. 2023년, 2024년 상반기(선도학교), 2026년 보급사업을 관리합니다. dibeot.kr 인터넷에서 ‘디벗관리시스템’ 검색 시 나오는 포털입니다. 양품화 일정·수거 업무 문의라면 양품화 업무용 디벗관리시스템 접속 여부를 먼저 확인합니다. KT에서 dibeot.kr의 상세 사용 방법을 공유하지 않아 현재 안내할 수 없습니다. 로그인 문제 · 아이디·비밀번호를 모를 때 학교 업무용 이메일 계정에서 kone_sen@k-one.co.kr로 보내거나 1566-6647 내선 1번으로 문의합니다. 아이디를 모르면 학교명을 알려 학교 계정을 확인합니다. 비밀번호를 모르면 초기화를 요청합니다. 안내받은 임시 비밀번호로 로그인한 뒤 프로필 정보 변경에서 새 비밀번호를 설정합니다. 일정 문제 · 승인완료 후 변경 신청이 되지 않는 경우 담당 양품화 매니저에게 직접 연락해 일정을 조정합니다. 승인 후 일정 변경 안내 삼성 크롬북 한정 · 고무 루버·키보드 부품 서비스 노트북 형태의 삼성 크롬북에 한정된 안내입니다. 해당 기기의 하단 고무 루버가 없어도 양품화 본체 수거는 가능하지만, 양품화 과정에서 새로 부착하거나 지급하지 않습니다. 루버가 필요하면 별도 서비스를 신청하며, 키보드 고무 마킹 등 부품의 수리·교체도 A/S를 요청하도록 안내합니다. 삼성 크롬북 루버 가격·프론트/리어 구매 구분 을 확인하고, 위 문의 유형별 경로에서 기기의 보급 연도·관리 사업자에 맞는 A/S 경로를 이용하세요. 상담원용 · 케이원(LG) 콜센터 연결 조작 방법 비용·기기 고장·분실 문의는 케이원(LG헬로비전) 콜센터 ARS 1번으로 연결합니다. 부담률과 파손 시 상한액은 파손·분실 시 본인부담 기준 을 확인하세요. Call 버튼 오른쪽의 파란색 전화 아이콘을 클릭합니다. 07077309024 를 입력하고 OK 버튼을 누릅니다. 연결 후 사용자에게 ARS에서 1번(케이원·LG헬로비전) 을 누르도록 안내합니다. 선택이 끝나면 유선 통화는 자동으로 종료됩니다."},{"path":"pages/relocation/","title":"디벗 재배치·여분 기기 회수","text":"홈 / 디벗 재배치·여분 기기 재배치·여분 기기 회수 양품화와 별도 업무 디벗 재배치·여분 기기 회수 양품화 작업 기간과 연계해 진행되지만, 양품화 사업과 구분되는 여분 기기 회수입니다. 학교가 할 일 2026년 9월 여분 현황 조사에서 회수 가능 물량을 제출하고 해당 공문을 받은 학교는 회수 대상 여부를 확인합니다. 회수 대상 디벗의 연도별 수량과 부속품을 확인해 반납을 준비합니다. 양품화 수거 대상 기기와 여분 기기를 섞이지 않게 구분합니다. 회수 물품은 디벗 본체와 키보드 케이스, 펜슬 등 부속품 일체입니다. 일정 확인 공문을 받은 양품화 대상 학교는 여분 기기 회수 일정이 양품화 수거 일정과 같습니다. 학교별 회수 내역과 일자는 해당 학교에 안내된 공문 붙임에서 확인합니다. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 안내된 수거일이 지난 경우 안내된 수거일이 지난 경우에는 사업자가 학교에 개별 연락해 일정을 협의합니다."},{"path":"pages/school/collection-day.html","title":"수거 당일 대응","text":"홈 / 학교 측 업무 프로세스 / 수거 당일 대응 학교 측 업무 프로세스 전체 목록 학교 측 업무 프로세스 · 04단계 수거 당일 대응 학교가 할 일 확정된 방문 일정에 맞춰 기기를 충전하고 한곳에 모아 둡니다. 수거담당자가 수거 대상 모든 기기의 전원을 켜 외관과 기본동작을 점검하는지 확인합니다. 전용펜 정상·비정상 분류 결과를 확인합니다. 수거담당자가 작성·서명한 수거확인서를 확인한 뒤 학교 확인 서명을 진행합니다. 확인 방법 [점검표관리]에서 당일 기기별 점검내역, [수거확인서]에서 수량·특이사항을 확인합니다. 수거확인서 확인·서명 절차 수거 당일 필수 확인 미충전 기기는 현장에서 점검할 수 없어 수거하지 않습니다. 충전 후 2차 방문해 기본점검을 마친 뒤 수거합니다. 기기를 충전 완료 후 한곳에 모아 두면 수거 시간을 줄일 수 있습니다. 전용펜은 수거일에 점검하며 펜 점검만을 위한 추가 방문은 없습니다. 수리·제공 기준은 펜슬 취급·제공 안내 를 확인하세요. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 수리 중 기기가 있는 경우 · 인계 방법 수리 완료 후 학교 반납 없이 바로 양품화로 이동하도록 확인하고, A/S 접수번호 또는 S/N을 담당자에게 제공합니다. 예외 · 수거 당일 현장에서 수리 필요 기기를 발견한 경우 수리동의서 제출 여부를 확인합니다. 제출된 기기는 현장 수거 후 바로 양품화를 진행합니다. 수리동의서 접수 방식·제출 현황 확인 분할 수거하는 경우 여러 번 수거하면 차수마다 별도 수거확인서가 작성됩니다. 차수별로 수량·특이사항을 확인합니다. 한시적 별도 사업 · 애플 1세대 펜슬 배터리 불량 반납 일반 전용펜과 구분되는 별도 사업입니다. 반납 대상·사전 보고·실물 제출 조건은 펜슬 배터리 불량 교환 안내 에서 확인하세요. 이전: 일정 확인·변경 전체 목록 다음: 수거확인서"},{"path":"pages/school/","title":"학교 측 업무 프로세스","text":"홈 / 학교 측 업무 프로세스 학교 업무 학교 측 업무 프로세스 학교 업무 단계 01 수거 전 준비 02 OS별 준비 03 일정 확인·변경 04 수거 당일 대응 05 수거확인서 06 명렬표·관리전환 07 배부·설치 확인"},{"path":"pages/school/installation.html","title":"배부·설치 확인","text":"홈 / 학교 측 업무 프로세스 / 배부·설치 확인 학교 측 업무 프로세스 전체 목록 학교 측 업무 프로세스 · 07단계 배부·설치 확인 학교가 할 일 명렬표와 필요한 계정·설정 자료가 취합되었는지 확인합니다. 담당 매니저와 일정을 사전 협의하고, [배송·설치일정]에서 공문에 안내된 학교명·방문 날짜·시간·수량을 확인합니다. 기기·구성품 수량과 기기·설정을 검사합니다. [설치확인서]에서 설치 수량·담당자 기록을 확인합니다. 설치담당자 서명 후 학교 확인자 정보·서명을 입력하고 [학교 확인 서명 및 잠금]을 진행합니다. 확인 방법 일정 확정·변경과 상태 확인은 수거 일정과 같은 방식 입니다. 학교 서명 후 확인서 [잠금]과 배송·설치일정의 [처리완료]를 확인합니다. 설치확인서는 출력해 보관할 수 있습니다. OS별 배부 구성품 OS 디벗 전용펜·펜촉 펜슬 캡 북커버 케이스 보호 케이스 화면보호필름 펜슬 건전지 안드로이드 O O - O - O - 크롬 O O - - O O - 아이패드 O O 일부 수량 O - O - 윈도우 O O - - O O - 웨일 O O - - O O O 전용펜과 충전기는 양품화 대상이 아닙니다. 신규 지급 구성품은 펜촉·펜슬 캡·펜슬이며, 펜슬 제공 수량·제품은 펜슬 취급·제공 안내 에서 확인합니다. 그 밖의 품목은 부족하고 교체가 필요하면 구매합니다. 충전기·케이블은 학교에서 보관합니다. 펜슬 제공 확인 2027년 실제 보급 대수에 따른 OS별 제공 수량·제품은 펜슬 취급·제공 안내 에서 확인합니다. 서명 전 필수 확인 설치확인서 내용이 실제와 다르면 서명 전에 담당자에게 알립니다. 담당자 선행 서명이 있어야 학교 확인 서명을 할 수 있으며, 서명 후 확인서는 잠깁니다. 학교별 배부 일정은 담당 매니저와 사전 협의해 확정하며, 방문 일정은 교육청 공문으로 안내됩니다. 최종 배부 수량은 공문 및 디벗관리시스템에서 확인합니다. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 한시적 별도 사업 · 애플 1세대 펜슬 배터리 불량 교환 대상·사전 보고·실물 반납 조건과 교환 수량은 펜슬 안내의 한시적 교환사업 에서 확인합니다. 이전: 명렬표·관리전환 전체 목록"},{"path":"pages/school/os-preparation.html","title":"OS별 준비","text":"홈 / 학교 측 업무 프로세스 / OS별 준비 학교 측 업무 프로세스 전체 목록 학교 측 업무 프로세스 · 02단계 OS별 준비 학교가 할 일 모든 OS에서 개인데이터 삭제 여부를 확인합니다. 차단 정책 해제는 학교가 관리자 계정으로 직접 수행하며, 수거담당자는 완료 여부를 확인합니다. OS별 수합 전·후 준비 OS 수합 전·학생 점검 수합 완료 후 학교 작업 안드로이드 FamilyLink 로그아웃, 화면 잠금 ‘설정 안 함’, 개인데이터 삭제, 구글·삼성 계정 로그아웃 로그아웃 확인 후 AMA·Android 정책 모두에서 공장초기화·USB 전송 차단 해제 아이패드 학교가 먼저 계정 로그아웃 차단을 허용. 학생의 개인데이터 삭제, 화면 잠금 해제, ASM 계정 로그아웃 공장초기화, USB 제한 모드·USB 파일 연결, 외부 PC 페어링 차단을 허용으로 변경 윈도우 개인데이터 삭제 USB 제한 모드·USB 파일 연결 차단 해제 크롬북 개인데이터 삭제 구글 관리 콘솔에서 파워워시·게스트 모드 허용 웨일북 개인데이터 삭제, 웨일스페이스 계정 로그아웃 웨일스페이스 어드민에서 양품화 단말을 관리 기기에서 제외 확인 방법 안드로이드: 적용된 AMA·Android 정책을 모두 확인합니다. 차단 목록의 공장초기화·USB 전송을 허용하고 저장합니다. 아이패드: 적용 정책 [상세] → 차단 영역 [수정]에서 계정 설정, 공장초기화, USB 관련 항목과 외부 PC 페어링을 확인합니다. 윈도우: 적용 정책 [상세] → 차단 영역 [수정]에서 USB 제한 모드·USB 파일 연결을 허용하고 저장합니다. 크롬북: 구글 관리자 페이지의 기기 → 설정 → 해당 조직 단위에서 파워워시 허용과 게스트 모드 허용 상태를 확인합니다. 웨일북: 시간 영역 → 전원 → 로그아웃을 확인합니다. 어드민의 Whalespace 관리 기기 → 모든 기기 보기 → 대상 선택 → 관리 기기에서 제외 후 안내에 동의하고 확인합니다. 안드로이드는 개별 [저장] 후 하단 [변경 사항 저장] → [확인]까지 진행합니다. 아이패드·윈도우에는 이 하단 메뉴가 없을 수 있으므로 표시 여부를 확인하세요. 준비 시 필수 확인 초기화 차단 해제는 필수, 기기 초기화는 선택 입니다. 계정 삭제·로그아웃·차단 해제와 초기화 실행을 같은 작업으로 안내하지 마세요. 웨일북은 별도 초기화 차단 기능과 관계없이 관리 기기에서 제외해야 합니다. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 계정·잠금 해제를 완료하지 못한 경우 학교 준비를 완료하고 미해제 항목은 담당자에게 확인하세요. OS별 처리 가능 여부가 다릅니다. 현장 판정·OS별 미해제 기기 안내 이전: 수거 전 준비 전체 목록 다음: 일정 확인·변경"},{"path":"pages/school/preparation.html","title":"수거 전 준비","text":"홈 / 학교 측 업무 프로세스 / 수거 전 준비 학교 측 업무 프로세스 · 01단계 수거 전 준비 학교가 할 일 ? 이 단계에서 학교가 진행할 절차입니다. 학생·학부모가 작성한 OS별 반납 점검표를 반별로 수합하고 기기 상태를 점검합니다. 점검표는 기기별로 붙이지 않고 학교가 보관합니다. 필요한 개인 자료를 미리 백업하도록 안내한 뒤 개인데이터 삭제·계정 로그아웃·화면 잠금 해제 여부를 확인합니다. 화면·버튼·카메라·전용펜과 불량·파손 여부를 확인하고 수리 필요 기기는 A/S를 요청합니다. 기기는 충전보관함에 충전하여 보관하고, 아이패드 애플펜슬도 충전합니다. 충전 완료 후 기기를 한곳에 모아 두면 수거 시간을 줄일 수 있습니다. 확인 방법 수거 전 준비 확인표 확인 항목 확인할 내용 반납 점검표 반별 수합·상태 점검 완료 여부. 학교 보관 기간은 6개월~1년입니다. 데이터·계정 개인정보 삭제, 해당 OS의 계정 로그아웃·잠금 해제·차단 정책 해제 여부 충전·작동 전원을 켜 외관·기본동작을 확인할 수 있는 상태인지 확인 수리 상태 수리 필요 기기의 A/S 진행·완료 여부 확인. 진행 중이거나 당일 불량을 발견하면 아래 추가 확인을 확인합니다. OS별 준비 상세 · A/S 문의 경로 수거 전 필수 확인 개인정보·계정 삭제와 반납 점검을 마치지 않은 기기는 사용자 확인이 필요합니다. 반납 점검표와 수리동의서를 혼동하지 마세요. 수거 시기 · 양품화 대상 수량 명렬표 수거 시기의 양품화 대상 수량 명렬표와 2월 신입생 수 확정 후 준비하는 명렬표 는 서로 다른 자료입니다. 양품화 대상 기기 수량 변경 요청은 서울시교육청 창의미래교육과 로 문의해 주세요. 수거 시기 명렬표를 이메일로 이미 보낸 경우에는 변경된 수량에 맞춰 명렬표를 수정한 후 다시 이메일로 보내 주세요. 명렬표 이메일 제출·재발송: kone_sen@k-one.co.kr 해당하는 경우 추가 확인 ? 기본 절차와 별도로, 해당 상황에 맞는 항목만 펼쳐 확인하세요. 해당 학교만 · 미점검 학교 명단 및 대상 기기 정보 제출 미점검 학교는 양품화 수거 대상 기기의 사용자·기기 정보를 제출 양식에 작성해 2027년 양품화 사업자 메일 kone_sen@k-one.co.kr 로 보냅니다. 양식에는 학교명, OS, 학년·반·번호, 사용자 정보와 기기 일련번호 등을 작성합니다. 3학년 디벗 사용 관리 현황은 필수이며, 그 외 학년 자료는 선택 제출입니다. 제출기한은 양식에 10.23.(금)으로 적혀 있으나 연도가 없습니다. 공문에서 해당 연도의 제출기한을 확인하세요. 회수일이 그보다 앞서면 회수일 기준 3일 전까지 제출합니다. 최신 자료를 디벗관리시스템 [명렬표 관리]에 이미 반영한 학교는 양식을 다시 보내지 않고 메일로 [반영 완료]라고 회신합니다. 수리 필요 기기가 있는 경우 · 수리동의서 신청·확인 디벗관리시스템에서 2027년 디벗 양품화 사업의 [수리동의서] 메뉴를 엽니다. 메뉴에서 학교 정보를 확인하고 화면에 표시된 QR을 수리가 필요한 학생·학부모에게 안내합니다. 학생·학부모는 QR에 접속해 학교를 선택하고 수리 동의를 신청합니다. 별도 수리동의서 양식은 없습니다. 학생·학부모가 안내받은 QR에서 학교를 선택해 신청합니다. 디벗관리시스템의 [수리동의서] 메뉴에서 제출한 학생·학부모 목록을 확인합니다. 수거 당일 제출 여부를 확인하고, 제출된 기기는 현장 수거 후 바로 양품화를 진행합니다. A/S 진행 중인 기기가 있는 경우 양품화 수거 전에 가능한 한 수령하고 수리 완료 예정일을 확인합니다. 수거 당일까지 수리 중이면 수리 중 기기 인계 안내 를 확인하세요. 전체 목록 다음: OS별 준비"},{"path":"pages/school/receipt.html","title":"수거확인서","text":"홈 / 학교 측 업무 프로세스 / 수거확인서 학교 측 업무 프로세스 전체 목록 학교 측 업무 프로세스 · 05단계 수거확인서 학교가 할 일 [수거확인서] 목록에서 우리 학교의 해당 차수를 선택합니다. 계획수량·본체 수거수량·미수거수량과 미수거 사유 등 특이사항을 확인합니다. 수거담당자 서명 후 학교 확인자의 직위·이름·연락처를 입력합니다. 마우스 또는 터치로 서명하고 [학교 확인 서명 및 잠금]에서 확인·저장합니다. 확인 방법 확인서 상단의 서명일과 [잠금], 회수일정의 [처리완료] 상태를 확인합니다. 수거 후 상세점검 내역은 회수 후 15일 이내에 시스템에서 확인할 수 있습니다. 서명 전 필수 확인 학교 서명을 완료하면 확인서가 잠깁니다. 수량과 특이사항을 확인한 뒤 서명하세요. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 분할 수거하는 경우 차수별 확인서를 각각 확인하고 서명합니다. 서명 버튼이 보이지 않는 경우 수거담당자 서명 대기 안내가 있으면 담당자의 선행 서명을 기다립니다. 서명 후 잠금 해제·정정이 필요한 경우 자료에 잠금 해제·정정 절차가 없으므로 담당자에게 문의 하세요. 확인 필요 · 확인서에 전용펜 수량이 표시되는 경우 확인 필요 전용펜은 현장 점검만 한다는 안내와 확인서의 수거수량 항목이 함께 있습니다. 확인서의 펜 수량이 무엇을 뜻하는지 서명 전에 확인하세요. 이전: 수거 당일 대응 전체 목록 다음: 명렬표·관리전환"},{"path":"pages/school/schedule.html","title":"일정 확인·변경","text":"홈 / 학교 측 업무 프로세스 / 일정 확인·변경 학교 측 업무 프로세스 전체 목록 학교 측 업무 프로세스 · 03단계 일정 확인·변경 학교가 할 일 학교에 발송된 공문에서 수거 일정을 확인합니다. 학교에 안내된 디벗관리시스템 에 발급된 학교 계정으로 로그인해 [2027년 디벗 양품화] → [회수일정]을 엽니다. 학교명·수거 날짜·시간·수량과 상태가 공문 내용과 일치하는지 확인합니다. 일정이 맞으면 [이 일정으로 확정]을 누릅니다. 수거 희망 일정 변경이 필요하면 [회수일정]에서 우리 학교의 [일정 변경 신청]을 선택하고 희망 날짜·시간, 변경 사유와 담당자 직통 연락처를 입력해 신청합니다. 확인 방법 일정 상태별 확인 상태 의미와 학교 대응 공문 수령 후 공문에 기재된 수거 일정과 시스템의 [회수일정] 정보를 확인합니다. 공지완료 공지 일정을 확인하고 확정 또는 변경 신청합니다. 변경신청 희망 일정을 신청한 상태입니다. 담당 매니저 확인을 기다립니다. 협의중 담당 매니저가 조정한 일정을 전화로 안내합니다. 협의 후 다시 확정 또는 변경 신청합니다. 승인완료 방문 날짜가 확정된 상태입니다. 처리완료 수거확인서의 학교 서명까지 완료된 상태입니다. 일정 화면을 열어 알림이 사라지거나 ‘공지확인’으로 기록되어도 방문 확정을 의미하지 않습니다. [승인완료] 상태를 확인하세요. 변경 신청일은 확정일이 아닙니다. 다수 학교 일정에 따라 담당 매니저와 조율합니다. 수거 전 사전 연락 수거 예정일 3일 전까지 수거 담당자가 학교로 연락드릴 예정입니다. 해당하는 경우 추가 확인 아래 항목은 해당 상황이나 대상 기기에 한해 펼쳐 확인하세요. 예외 · 승인완료 후 시스템에서 일정 변경 신청이 되지 않는 경우 담당 매니저가 확인해 [승인완료]로 처리한 일정은 시스템에서 변경 신청이 불가능할 수 있습니다. 이 경우 담당 양품화 매니저에게 직접 연락해 수거 일정을 조정해야 합니다. 일정 관련 문의 경로 접속 문제 · 검색으로 찾은 사이트가 다른 경우 인터넷 검색으로 나오는 dibeot.kr과 양품화 업무용 시스템은 별도입니다. 학교에 안내된 주소로 접속하세요. 시스템 접속 주소·용도 구분 자료 확인 · 시험서버 화면 예시 시험서버 화면 예시입니다. 화면에 표시된 일정·학교 값을 실제 정보로 사용하지 마세요. 이전: OS별 준비 전체 목록 다음: 수거 당일 대응"},{"path":"pages/school/transfer.html","title":"명렬표·관리전환","text":"홈 / 학교 측 업무 프로세스 / 명렬표·관리전환 학교 측 업무 프로세스 · 06단계 명렬표·관리전환 시기별 명렬표 구분 수거 시기와 신입생 수 확정 후 명렬표 구분 시기 명렬표 관련 안내 수거 시기 양품화 대상 수량 명렬표 수량 변경·이메일 수정 재발송 2월 신입생 수 확정 후 신입생 명렬표 이 단계의 명렬표·관리전환 안내 두 명렬표는 서로 다른 자료입니다. 수거 시기 명렬표의 이메일 재발송 안내와 신입생 명렬표 준비를 구분해 주세요. 학교가 할 일 ? 이 단계에서 학교가 진행할 절차입니다. 2월 신입생 수가 확정되면 학교별 OS와 신입생 명렬표를 준비하고 반 배정·최종 수량을 확인합니다. 2027년 디벗 양품화 사업의 [명렬표 관리]에서 개별 또는 엑셀 일괄 등록을 선택합니다. 일괄 등록은 [양식다운로드]로 받은 양식의 예시 2줄을 삭제하고 학년·반·번호·이름을 한 줄에 한 명씩 입력합니다. 작성한 .xlsx 파일을 업로드합니다. 계정·설정 관련 선택 항목은 해당 여부를 확인하고 시스템 안내에 따라 입력합니다. 확인 방법 등록 목록에서 학년·이름으로 검색하거나 [엑셀다운로드]로 등록 내용을 확인합니다. 학교 계정에서는 우리 학교 학생만 조회·등록됩니다. 2월 반 배정 및 학교별 수량 확정 후 대상 기기의 학교정보가 변경됩니다. OS별 관리전환 범위는 다음과 같습니다. OS별 관리전환 등록 범위 OS 전환 학교로 등록되는 항목 안드로이드·윈도우 MDM 아이패드 MDM 및 ASM 크롬북 CEU 웨일북 관리 기기 등록 전 필수 확인 명렬표가 없으면 확정 수량을 확인할 수 없습니다. 계정·비밀번호·명렬표는 시스템의 마스킹 상태를 유지해 취급합니다. 수거 시기 명렬표의 수량 변경 안내 수거 시기의 양품화 대상 수량 명렬표 수정·이메일 재발송은 수거 전 준비 안내 에서 확인해 주세요. 해당하는 경우 추가 확인 ? 기본 절차와 별도로, 해당 상황에 맞는 항목만 펼쳐 확인하세요. 엑셀 업로드 오류가 표시되는 경우 표시된 오류 항목의 해당 행을 수정해 다시 업로드합니다. SENEDU 학생 계정을 사용하는 경우 명렬표와 함께 취합하지 않으면 작업장 설치가 지연될 수 있습니다. 크롬북·웨일북 관리전환을 준비하는 경우 관리전환은 양품화 사업자가 진행하며, 필요한 관리자 접속 정보와 담당자 연락처는 디벗관리시스템으로 제출합니다. 크롬북은 2단계 인증이 필요합니다. 이전: 수거확인서 전체 목록 다음: 배부·설치 확인"},{"path":"pages/business/pencil.html","title":"펜슬 취급·제공 안내","text":"홈 / 양품화 사업 안내 / 펜슬 취급·제공 안내 양품화 사업 안내 전체 목록 품목별 상세 안내 펜슬 취급·제공 안내 전용펜 기본 취급 수거·점검·보관·A/S 구분 기본 안내 수거·보관 일반 전용펜은 양품화 수거 대상이 아닙니다. 학교에서 보관합니다. 현장 점검 기기 수거일에 상태를 점검해 정상·비정상을 표시합니다. 펜 점검만을 위한 추가 방문은 없습니다. 수거 전 준비 전용펜의 불량·파손 여부를 확인합니다. 아이패드 애플펜슬도 충전해 둡니다. 수리 필요 재배부 전 유지보수 업체 A/S를 진행합니다. 아래 한시적 배터리 불량 교환사업은 별도 대상·조건을 확인합니다. 보급 연도·관리 사업자별 A/S 문의 경로 펜슬 제공 기준 출처: 「2027년 디벗 양품화 펜슬 안내」 1쪽. 양품화사업자 펜슬 제공 기준 기기 구분 제공 수량 제공 펜슬 윈도우·안드로이드·크롬·웨일 실제 보급 대수의 15% 정품펜슬 애플(아이패드) 실제 보급 대수의 20% 호환펜슬 양품화 후 애플(아이패드) 기기용으로 신규 제공하는 펜슬은 모두 호환펜슬입니다. 양품화 전 파손 A/S로 제공받는 제품과 구분합니다. 회수 대수가 아닌 2027년 실제 보급 대수로 계산합니다. 신규 펜슬 제공률은 기본 15%입니다. 애플(아이패드)은 전용펜 대신 호환펜슬을 제공하므로 20%로 산정합니다. 계산 예시 · 회수 대수와 재보급 대수가 다른 경우 회수 수량과 재보급 수량은 다를 수 있습니다. 회수한 기기를 해당 학교 신입생 수에 맞춰 보급하고, 남은 수량은 기기가 부족한 다른 학교로 보급하므로 회수 대수가 아닌 실제 보급 대수로 계산합니다. 예: 100대를 회수하고 해당 OS 기기 80대를 실제 보급하면, 윈도우·안드로이드·크롬·웨일은 정품펜슬 12개(15%), 애플은 호환펜슬 16개(20%)를 제공합니다. 배부 시 확인 펜슬은 위 실제 보급 대수별 수량과 제품을 확인하고, 펜촉·펜슬 캡 등 구성품은 배부·설치 확인의 OS별 구성품 표 와 대조합니다. 그 밖의 품목은 부족하고 교체가 필요하면 구매합니다. 해당하는 경우 추가 확인 양품화 전 파손 A/S와 한시적 배터리 불량 교환은 대상·제공 제품이 다르므로 해당 항목을 펼쳐 확인하세요. 양품화 전 · 애플펜슬 1세대 파손 A/S 양품화 전에 사용 중인 애플펜슬 1세대가 파손되어 A/S를 맡기면 애플펜슬 USB-C 타입(C타입) 으로 제공됩니다. 양품화 후 신규 제공하는 호환펜슬 및 아래 한시적 배터리 불량 교환사업과 구분해 안내합니다. A/S 문의 경로 한시적 별도 사업 · 2024년 보급 애플 1세대 펜슬 배터리 불량 교환 출처: 「2027년 디벗 양품화 펜슬 안내」 2쪽. 서울시교육청의 한시적 예산 지원으로 2024년 보급 애플 정품펜슬(1세대) 중 배터리 불량품 을 호환펜슬로 교환합니다. 사전 조사에서 배터리 불량 수량을 보고합니다. 양품화 반납 시 실제 불량펜슬을 함께 제출합니다. 2027년 재보급 시 교환분 호환펜슬을 지급합니다. 교환 가능 수량은 사전 보고 수량과 실제 반납 수량 중 적은 수량입니다. 사전 보고 수량을 초과해 교환할 수 없습니다. 불량펜슬 교환 수량 예시 사전 보고 실제 반납 교환 가능 10개 7개 7개 10개 10개 10개 10개 13개 10개 미보고(0개) 5개 0개 양품화사업자 제공분과 교육청 예산 불량 교환분은 별도이며 합산합니다. 배터리 불량 교환 수량은 보급 대수의 15%·20% 제공분에 포함되지 않습니다. 예: 애플 100대 실제 보급과 불량펜슬 10개 보고·반납 시, 교환분 10개와 보급 제공분 20개를 합해 호환펜슬 30개를 지급합니다. 수거 대상·품목 기준 사업 안내 목록 배부·설치 확인"}];
const header = document.querySelector('#site-header .container');
const tools = document.createElement('div');
tools.className = 'page-tools';
tools.innerHTML = '<form class="site-search" role="search"><label class="visually-hidden" for="site-query">전체 페이지 검색</label><div class="search-controls"><input id="site-query" name="q" type="search" maxlength="200" placeholder="검색어 입력"><button type="submit">검색</button></div></form>';
header.append(tools);
const results = document.createElement('section');
results.className = 'search-results';
results.hidden = true;
results.setAttribute('aria-label', '전체 페이지 검색 결과');
results.innerHTML = '<p class="search-status" role="status" aria-live="polite" aria-atomic="true"></p><ul class="search-result-list"></ul>';
header.append(results);
header.querySelector('.print-button').addEventListener('click', () => window.print());
const normalize = value => value.normalize('NFKC').toLocaleLowerCase('ko').replace(/\s+/g, ' ').trim();
const highlightKey = 'manual-search-highlight';
// 접힌 추가 안내도 직접 링크·검색·인쇄에서 빠짐없이 확인할 수 있게 합니다.
const revealSupplement = element => {
  for (let current = element; current; current = current.parentElement) {
    if (current.matches('details.supplement')) current.open = true;
  }
};
const revealHashTarget = () => {
  if (!location.hash) return;
  let id;
  try { id = decodeURIComponent(location.hash.slice(1)); } catch (_) { return; }
  const target = document.getElementById(id);
  if (!target) return;
  revealSupplement(target);
  window.requestAnimationFrame(() => target.scrollIntoView({ block:'start' }));
};
window.addEventListener('hashchange', revealHashTarget);
let printDisclosureState = null;
window.addEventListener('beforeprint', () => {
  if (printDisclosureState) return;
  printDisclosureState = [...document.querySelectorAll('details.supplement')].map(detail => [detail, detail.open]);
  printDisclosureState.forEach(([detail]) => { detail.open = true; });
});
window.addEventListener('afterprint', () => {
  if (!printDisclosureState) return;
  printDisclosureState.forEach(([detail, open]) => { detail.open = open; });
  printDisclosureState = null;
});
const getRanges = (text, terms) => {
  let normalized = '';
  const starts = [];
  const ends = [];
  for (let offset = 0; offset < text.length;) {
    const point = String.fromCodePoint(text.codePointAt(offset));
    const value = point.normalize('NFKC').toLocaleLowerCase('ko');
    for (let i = 0; i < value.length; i++) {
      normalized += value[i];
      starts.push(offset);
      ends.push(offset + point.length);
    }
    offset += point.length;
  }
  const ranges = [];
  for (const term of terms) {
    let from = 0;
    while (from < normalized.length && ranges.length < 300) {
      const index = normalized.indexOf(term, from);
      if (index < 0) break;
      ranges.push([starts[index], ends[index + term.length - 1]]);
      from = index + Math.max(term.length, 1);
    }
  }
  ranges.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  const merged = [];
  for (const range of ranges) {
    const last = merged[merged.length - 1];
    if (last && range[0] <= last[1]) last[1] = Math.max(last[1], range[1]);
    else merged.push(range.slice());
  }
  return merged;
};
const appendHighlightedText = (container, text, terms) => {
  const ranges = getRanges(text, terms);
  let offset = 0;
  for (const [start, end] of ranges) {
    container.append(document.createTextNode(text.slice(offset, start)));
    const mark = document.createElement('mark');
    mark.className = 'search-highlight';
    mark.textContent = text.slice(start, end);
    container.append(mark);
    offset = end;
  }
  container.append(document.createTextNode(text.slice(offset)));
};
const applyPageHighlights = query => {
  const terms = normalize(query).split(' ').filter(Boolean);
  const main = document.querySelector('main');
  if (!terms.length || !main) return;
  const walker = document.createTreeWalker(main, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      return node.nodeValue.trim() && !node.parentElement.closest('script,style,noscript,textarea,input,select,button,mark,[aria-hidden="true"]')
        ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
    }
  });
  const nodes = [];
  while (walker.nextNode()) nodes.push(walker.currentNode);
  let count = 0;
  for (const node of nodes) {
    const ranges = getRanges(node.nodeValue, terms);
    if (!ranges.length) continue;
    const fragment = document.createDocumentFragment();
    appendHighlightedText(fragment, node.nodeValue, terms);
    count += ranges.length;
    node.parentNode.replaceChild(fragment, node);
  }
  if (!count) return;
  main.querySelectorAll('mark.search-highlight').forEach(revealSupplement);
  const notice = document.createElement('div');
  notice.className = 'search-highlight-notice';
  const label = document.createElement('span');
  label.textContent = `검색어 “${query}” ${count}곳 강조 중`;
  const clearButton = document.createElement('button');
  clearButton.type = 'button';
  clearButton.className = 'search-highlight-clear';
  clearButton.textContent = '강조 해제';
  const clear = () => {
    main.querySelectorAll('mark.search-highlight').forEach(mark => mark.replaceWith(document.createTextNode(mark.textContent)));
    notice.remove();
    document.removeEventListener('keydown', onKeyDown);
  };
  const onKeyDown = event => { if (event.key === 'Escape') clear(); };
  clearButton.addEventListener('click', clear);
  document.addEventListener('keydown', onKeyDown);
  notice.append(label, clearButton);
  main.prepend(notice);
};
// 수집은 기존 검색과 독립적으로 실패합니다. 본문·제목·전체 URL은 전송하지 않습니다.
const collection = (() => {
  try {
  const sessionKey = 'oneai-session-v1';
  const navigationKey = 'oneai-navigation-v1';
  const base = new URL(root || './', location.href);
  const catalog = [
    ['./', 'manual-home'], ['pages/business/', 'business-index'],
    ['pages/business/overview.html', 'business-overview'],
    ['pages/business/collection.html', 'business-collection'],
    ['pages/business/inspection.html', 'business-inspection'],
    ['pages/business/distribution.html', 'business-distribution'],
    ['pages/business/pencil.html', 'business-pencil'],
    ['pages/school/', 'school-index'],
    ['pages/school/preparation.html', 'school-preparation'],
    ['pages/school/os-preparation.html', 'school-os-preparation'],
    ['pages/school/schedule.html', 'school-schedule'],
    ['pages/school/collection-day.html', 'school-collection-day'],
    ['pages/school/receipt.html', 'school-receipt'],
    ['pages/school/transfer.html', 'school-transfer'],
    ['pages/school/installation.html', 'school-installation'],
    ['pages/relocation/', 'relocation-index'], ['pages/contact.html', 'contact']
  ];
  const canonical = url => url.pathname.replace(/index\.html$/, '');
  const identify = path => {
    const url = new URL(path, base);
    const entry = catalog.find(([path]) => canonical(new URL(path, base)) === canonical(url));
    return entry ? { documentId:entry[1], documentVersion:'local-v1' } : null;
  };
  const currentDocument = identify(location.href);
  const local = /^https?:$/.test(location.protocol) && ['localhost', '127.0.0.1', '[::1]'].includes(location.hostname);
  const ui = document.createElement('form');
  ui.className = 'collection-session';
  ui.innerHTML = '<div class="session-account"><span class="session-identity">로그인하지 않음</span><button type="button" class="session-login">분석용 로그인</button><button type="button" class="session-logout" hidden>로그아웃</button></div><p class="session-status" role="status" aria-live="polite"></p><dialog class="session-dialog" aria-labelledby="session-title" aria-describedby="session-description"><h2 id="session-title">ONE AI 분석용 로그인</h2><p id="session-description">비밀번호 없이 분석용 USERID로 수집 세션을 시작합니다. 합성 ID와 테스트 검색어만 사용하세요.</p><label for="session-user-id">USERID</label><input id="session-user-id" name="userId" maxlength="64" placeholder="예: 테스트상담사" required autocomplete="off" aria-describedby="session-id-hint"><p id="session-id-hint">원하는 이름을 최대 64자로 입력하세요. 실제 이름·회사 ID·개인정보는 사용하지 마세요.</p><div class="session-actions"><button type="button" class="session-cancel">취소</button><button type="submit">로그인 · 세션 시작</button></div></dialog>';
  header.append(ui);
  const status = ui.querySelector('.session-status');
  const dialog = ui.querySelector('.session-dialog');
  const loginButton = ui.querySelector('.session-login');
  const logoutButton = ui.querySelector('.session-logout');
  let session = null;
  let queue = Promise.resolve();
  let pending = 0;
  const safe = action => { try { return action(); } catch (_) { return null; } };
  const uuid = () => crypto.randomUUID();
  // UI는 세션 시작·종료 결과만 표시합니다. 추후 인증 연동은 이 생명주기 경계에서 처리합니다.
  const renderSession = () => {
    ui.querySelector('.session-identity').textContent = session ? `${session.userId} · 세션 사용 중` : '로그인하지 않음';
    loginButton.hidden = !!session;
    logoutButton.hidden = !session;
  };
  const reset = () => {
    session = null;
    safe(() => { sessionStorage.removeItem(sessionKey); sessionStorage.removeItem(navigationKey); });
    renderSession();
  };
  const validLabel = label => typeof label === 'string' && label.trim().length > 0 && label.length <= 64 && !/[\u0000-\u001f\u007f-\u009f]/.test(label);
  const validSession = value => !!value && validLabel(value.userId) && typeof value.sessionId === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value.sessionId);
  const startSession = label => {
    if (!validLabel(label)) throw new Error('invalid label');
    // 먼저 저장에 성공한 세션만 활성화하며 기존 세션을 임의로 재사용하지 않습니다.
    const next = { userId:label, sessionId:uuid() };
    sessionStorage.setItem(sessionKey, JSON.stringify(next));
    sessionStorage.removeItem(navigationKey);
    session = next;
    renderSession();
  };
  const navigation = safe(() => {
    const value = JSON.parse(sessionStorage.getItem(navigationKey) || 'null');
    sessionStorage.removeItem(navigationKey);
    return value;
  });
  // 새 탭은 명시적으로 시작하고 일반 이동·새로고침에서만 세션을 복원합니다.
  const navigationType = performance.getEntriesByType('navigation')[0]?.type;
  safe(() => {
    const stored = JSON.parse(sessionStorage.getItem(sessionKey) || 'null');
    const internal = navigation && navigation.target === canonical(location) && Date.now() - navigation.at < 10000;
    if (!window.opener && validSession(stored) && (navigationType === 'reload' || internal)) session = stored;
    else reset();
  });
  const send = (eventType, payload, eventId = uuid()) => {
    if (!validSession(session) || !local || pending >= 100) return null;
    const owner = session;
    const event = { schemaVersion:1, eventId, eventType, occurredAt:new Date().toISOString(), ...owner, payload };
    const body = JSON.stringify(event);
    pending++;
    queue = queue.then(async () => {
      if (session !== owner || !validSession(owner)) return;
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 3000);
      try {
        const response = await fetch(new URL('api/v1/events', base), {
          method:'POST', headers:{ 'Content-Type':'application/json' }, body,
          signal:controller.signal, keepalive:true, credentials:'omit', redirect:'error'
        });
        const result = await response.json();
        if (!response.ok || result.eventId !== eventId || !['stored', 'duplicate'].includes(result.status)) throw new Error('collection failed');
        if (session === owner) status.textContent = '수집 API 저장 확인';
      } catch (_) {
        if (session === owner) status.textContent = '전송 실패: 검색·열람은 계속 사용할 수 있습니다.';
      } finally { clearTimeout(timer); }
    }).catch(() => {}).finally(() => { pending--; });
    return eventId;
  };
  const view = context => safe(() => {
    if (!currentDocument) return;
    if (!validSession(session)) return;
    const linked = context?.sessionId === session.sessionId && context.target === canonical(location) && Date.now() - context.at < 10000 && context.clickId;
    const viewId = uuid();
    send('document_viewed', { viewId, ...currentDocument, source:linked ? 'search' : 'unknown',
      searchId:linked ? context.searchId : null, impressionId:linked ? context.impressionId : null, clickId:linked ? context.clickId : null }, viewId);
  });
  ui.addEventListener('submit', event => {
    event.preventDefault();
    try {
      if (!ui.reportValidity()) return;
      const label = ui.elements.userId.value.trim();
      if (!validLabel(label)) {
        ui.elements.userId.setCustomValidity('ID를 1~64자로 입력하세요. 제어문자는 사용할 수 없습니다.');
        ui.reportValidity(); return;
      }
      if (!local) { status.textContent = '수집은 localhost HTTP 실행에서 사용합니다. 검색은 계속 사용할 수 있습니다.'; dialog.close(); return; }
      startSession(label);
      dialog.close();
      status.textContent = '합성 USERID·검색어만 사용하세요. 세션 시작됨';
      view(null);
    } catch (_) { reset(); status.textContent = '세션 시작 불가: 브라우저 저장소와 실행 환경을 확인하세요.'; }
  });
  ui.elements.userId.addEventListener('input', () => ui.elements.userId.setCustomValidity(''));
  loginButton.addEventListener('click', () => { dialog.showModal(); ui.elements.userId.focus(); });
  ui.querySelector('.session-cancel').addEventListener('click', () => dialog.close());
  dialog.addEventListener('close', () => { if (!session) loginButton.focus(); });
  logoutButton.addEventListener('click', () => {
    reset(); ui.elements.userId.value = ''; status.textContent = '로그아웃되었습니다. 검색·열람은 계속 사용할 수 있습니다.'; loginButton.focus();
  });
  document.addEventListener('click', event => safe(() => {
    if (!validSession(session) || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const link = event.target.closest('a');
    if (!link || link.target === '_blank') return;
    const url = new URL(link.href);
    if (url.origin !== location.origin || canonical(url) === canonical(location)) return;
    // 검색 클릭에서 기록한 연결은 덮어쓰지 않습니다.
    const saved = JSON.parse(sessionStorage.getItem(navigationKey) || 'null');
    if (saved?.clickId && saved.target === canonical(url) && Date.now() - saved.at < 1000) return;
    sessionStorage.setItem(navigationKey, JSON.stringify({ target:canonical(url), at:Date.now(), sessionId:session.sessionId }));
  }));
  status.textContent = session ? '합성 수집 세션 유지 중' : '합성 USERID로 시작하세요. 시작 전에는 수집하지 않습니다.';
  if (session) ui.elements.userId.value = session.userId;
  renderSession();
  view(navigation);
  return {
    search: (query, matches) => safe(() => {
      if (!validSession(session)) return null;
      const items = matches.map((item, index) => ({ ...identify(item.path), rank:index + 1 }));
      if (items.some(item => !item.documentId)) return null;
      const searchId = uuid();
      send('search_submitted', { searchId, query, sourceDocumentId:currentDocument?.documentId || null }, searchId);
      const impressionId = send('results_exposed', { searchId, totalCount:items.length, items });
      return { searchId, impressionId, items, sessionId:session.sessionId };
    }),
    click: (context, index, link, event) => safe(() => {
      if (!validSession(session) || context?.sessionId !== session.sessionId || !context.impressionId) return;
      const item = context.items[index];
      const clickId = send('document_clicked', { searchId:context.searchId, impressionId:context.impressionId, ...item });
      if (clickId && event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey) {
        sessionStorage.setItem(navigationKey, JSON.stringify({ ...context, items:undefined, clickId, target:canonical(new URL(link.href)), at:Date.now() }));
      }
    })
  };
  } catch (_) { return { search:() => null, click:() => null }; }
})();
tools.querySelector('form').addEventListener('submit', event => {
  event.preventDefault();
  const query = tools.querySelector('input').value.trim();
  const terms = normalize(query).split(' ').filter(Boolean);
  const list = results.querySelector('ul');
  list.replaceChildren();
  results.hidden = false;
  const status = results.querySelector('.search-status');
  if (!terms.length) {
    status.textContent = '검색어를 입력하세요.';
    return;
  }
  const matches = searchPages.filter(item => terms.every(term => normalize(item.title + ' ' + item.text).includes(term)));
  status.textContent = matches.length ? '검색 결과 ' + matches.length + '건' : '검색 결과가 없습니다. 다른 검색어를 입력하세요.';
  let collectionContext = null;
  matches.forEach((item, index) => {
    const li = document.createElement('li');
    const link = document.createElement('a');
    link.href = root + item.path;
    link.textContent = item.title;
    const snippet = document.createElement('p');
    const lowerText = normalize(item.text);
    const firstMatch = Math.min(...terms.map(term => lowerText.indexOf(term)).filter(position => position >= 0));
    const start = Number.isFinite(firstMatch) ? Math.max(0, firstMatch - 35) : 0;
    const snippetText = (start ? '…' : '') + item.text.slice(start, start + 160) + (item.text.length > start + 160 ? '…' : '');
    appendHighlightedText(snippet, snippetText, terms);
    link.addEventListener('click', event => {
      collection.click(collectionContext, index, link, event);
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      try { sessionStorage.setItem(highlightKey, query); } catch (_) {}
    });
    li.append(link, snippet);
    list.append(li);
  });
  collectionContext = collection.search(query, matches);
});
try {
  const query = sessionStorage.getItem(highlightKey);
  sessionStorage.removeItem(highlightKey);
  if (query) applyPageHighlights(query);
} catch (_) {}
revealHashTarget();
})();


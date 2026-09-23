const elements = {
    track: document.querySelector('#track'),
    raceId: document.querySelector('#raceId'),
    raceStatus: document.querySelector('#raceStatus'),
    racerCount: document.querySelector('#racerCount'),
    passage: document.querySelector('#passage'),
    typingInput: document.querySelector('#typingInput'),
    typingPanel: document.querySelector('#typingPanel'),
    typingProgress: document.querySelector('#typingProgress'),
    position: document.querySelector('#positionMetric'),
    wpm: document.querySelector('#wpmMetric'),
    accuracy: document.querySelector('#accuracyMetric'),
    degrees: document.querySelector('#degreeMetric'),
    steps: document.querySelector('#stepMetric'),
    distance: document.querySelector('#distanceMetric'),
    joinModal: document.querySelector('#joinModal'),
    joinForm: document.querySelector('#joinForm'),
    driverName: document.querySelector('#driverName'),
    formError: document.querySelector('#formError'),
    serverAddress: document.querySelector('#serverAddress'),
    joinAddress: document.querySelector('#joinAddress'),
    copyApi: document.querySelector('#copyApiButton'),
    restart: document.querySelector('#restartButton'),
    toast: document.querySelector('#toast')
};

const state = {
    race: null,
    participantId: Number(sessionStorage.getItem('participantId')) || null,
    typed: '',
    errors: 0,
    sending: false,
    queued: false,
    pollTimer: null,
    toastTimer: null
};

const api = async (path, options = {}) => {
    const response = await fetch(path, {
        ...options,
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || `Request failed (${response.status})`);
    return body;
};

function ordinal(number) {
    const suffix = number % 100 >= 11 && number % 100 <= 13 ? 'TH' : ({ 1: 'ST', 2: 'ND', 3: 'RD' }[number % 10] || 'TH');
    return `${number}${suffix}`;
}

function setText(node, value) { node.textContent = String(value); }

function showToast(message) {
    setText(elements.toast, message);
    elements.toast.classList.add('show');
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(() => elements.toast.classList.remove('show'), 1800);
}

function renderPassage() {
    if (!state.race?.passage) return;
    const passage = state.race.passage;
    const completed = state.typed.length;
    elements.passage.replaceChildren();
    const correct = document.createElement('span');
    correct.className = 'correct';
    correct.textContent = passage.slice(0, completed);
    const current = document.createElement('span');
    current.className = 'current';
    current.textContent = passage.charAt(completed) || '';
    const pending = document.createElement('span');
    pending.className = 'pending';
    pending.textContent = passage.slice(completed + 1);
    elements.passage.append(correct, current, pending);
    elements.typingProgress.style.width = `${Math.min(100, completed / passage.length * 100)}%`;
}

function makeLane(participant) {
    const lane = document.createElement('div');
    lane.className = `lane${participant.id === state.participantId ? ' me' : ''}`;

    const driver = document.createElement('div');
    driver.className = 'driver';
    const name = document.createElement('strong');
    name.textContent = participant.name;
    const type = document.createElement('span');
    type.textContent = participant.bot ? 'CPU DRIVER' : (participant.id === state.participantId ? 'YOU / LIVE' : 'LAN DRIVER');
    driver.append(name, type);

    const road = document.createElement('div');
    road.className = 'road';
    const carWrap = document.createElement('div');
    carWrap.className = 'car-wrap';
    carWrap.style.left = `${Math.min(92, participant.progressPercent * .92)}%`;
    carWrap.style.setProperty('--car', participant.color);
    const car = document.createElement('span');
    car.className = 'car';
    const wheelOne = document.createElement('i');
    wheelOne.className = 'wheel one';
    const wheelTwo = document.createElement('i');
    wheelTwo.className = 'wheel two';
    carWrap.append(car, wheelOne, wheelTwo);
    road.append(carWrap);

    const stat = document.createElement('div');
    stat.className = 'lane-stat';
    const rank = document.createElement('strong');
    rank.textContent = ordinal(participant.position);
    const speed = document.createElement('span');
    speed.textContent = `${Math.round(participant.wpm)} WPM`;
    stat.append(rank, speed);
    lane.append(driver, road, stat);
    return lane;
}

function renderRace(race) {
    state.race = { ...state.race, ...race };
    setText(elements.raceId, race.raceId || '--------');
    setText(elements.raceStatus, race.status === 'RUNNING' ? 'RACE IN PROGRESS' : race.status === 'FINISHED' ? 'HEAT COMPLETE' : 'WAITING FOR DRIVER');
    const humans = race.participants?.filter(participant => !participant.bot).length || 0;
    setText(elements.racerCount, humans);
    elements.track.replaceChildren();
    if (!race.participants?.length) {
        const empty = document.createElement('div');
        empty.className = 'empty-track';
        empty.textContent = 'WAITING FOR RACERS';
        elements.track.append(empty);
    } else {
        race.participants.slice(0, 6).forEach(participant => elements.track.append(makeLane(participant)));
    }

    const me = race.participants?.find(participant => participant.id === state.participantId);
    if (me) {
        setText(elements.position, ordinal(me.position));
        setText(elements.wpm, Math.round(me.wpm));
        setText(elements.accuracy, me.accuracyPercent.toFixed(1));
        setText(elements.degrees, me.rotation.degrees.toFixed(1));
        setText(elements.steps, `${me.rotation.steps} STEPS · ${race.stepAngleDegrees.toFixed(1)}°`);
        setText(elements.distance, me.distanceMeters.toFixed(2));
        if (me.finished) showToast(`FINISHED ${ordinal(me.position)} · ${Math.round(me.wpm)} WPM`);
    }
}

async function sendProgress() {
    if (!state.participantId || state.sending) {
        state.queued = true;
        return;
    }
    state.sending = true;
    try {
        const race = await api(`/api/race/participants/${state.participantId}/progress`, {
            method: 'PUT',
            body: JSON.stringify({ typedCharacters: state.typed.length, errors: state.errors })
        });
        renderRace(race);
    } catch (error) {
        showToast(error.message);
    } finally {
        state.sending = false;
        if (state.queued) {
            state.queued = false;
            sendProgress();
        }
    }
}

function handleTyping() {
    if (!state.participantId || !state.race?.passage || state.typed.length >= state.race.passage.length) return;
    const entered = elements.typingInput.value;
    if (state.race.passage.startsWith(entered) && entered.length >= state.typed.length) {
        state.typed = entered;
    } else {
        state.errors += 1;
        elements.typingPanel.classList.remove('error');
        void elements.typingPanel.offsetWidth;
        elements.typingPanel.classList.add('error');
        elements.typingInput.value = state.typed;
    }
    renderPassage();
    sendProgress();
}

async function poll() {
    try {
        const race = await api('/api/race/distances');
        renderRace(race);
    } catch (error) {
        setText(elements.raceStatus, 'SERVER OFFLINE');
    } finally {
        state.pollTimer = setTimeout(poll, 400);
    }
}

async function bootstrap() {
    const displayAddress = location.host;
    setText(elements.serverAddress, displayAddress);
    setText(elements.joinAddress, location.origin);
    try {
        const race = await api('/api/race');
        state.race = race;
        const existing = race.participants.find(participant => participant.id === state.participantId);
        if (existing) {
            state.typed = race.passage.slice(0, existing.typedCharacters);
            state.errors = existing.errors;
            elements.typingInput.value = state.typed;
            elements.joinModal.classList.add('hidden');
        } else {
            state.participantId = null;
            sessionStorage.removeItem('participantId');
        }
        renderPassage();
        renderRace(race);
        poll();
    } catch (error) {
        setText(elements.formError, 'Could not reach the race server.');
    }
}

elements.joinForm.addEventListener('submit', async event => {
    event.preventDefault();
    const submit = elements.joinForm.querySelector('button[type="submit"]');
    submit.disabled = true;
    setText(elements.formError, '');
    try {
        const color = new FormData(elements.joinForm).get('color');
        const response = await api('/api/race/participants', {
            method: 'POST',
            body: JSON.stringify({ name: elements.driverName.value, color })
        });
        state.participantId = response.participantId;
        sessionStorage.setItem('participantId', String(response.participantId));
        state.race = response.race;
        state.typed = '';
        state.errors = 0;
        elements.typingInput.value = '';
        elements.joinModal.classList.add('hidden');
        renderPassage();
        renderRace(response.race);
        setTimeout(() => elements.typingInput.focus(), 250);
    } catch (error) {
        setText(elements.formError, error.message);
    } finally {
        submit.disabled = false;
    }
});

elements.typingPanel.addEventListener('click', () => elements.typingInput.focus());
elements.typingInput.addEventListener('input', handleTyping);
elements.typingInput.addEventListener('keydown', event => {
    if (event.key === 'Backspace' || event.key === 'Delete') event.preventDefault();
});

elements.restart.addEventListener('click', async event => {
    event.stopPropagation();
    if (!state.participantId) return;
    try {
        const race = await api('/api/race/reset', { method: 'POST', body: '{}' });
        state.typed = '';
        state.errors = 0;
        elements.typingInput.value = '';
        renderPassage();
        renderRace(race);
        elements.typingInput.focus();
        showToast('NEW HEAT STARTED FOR EVERYONE');
    } catch (error) {
        showToast(error.message);
    }
});

elements.copyApi.addEventListener('click', async () => {
    const url = `${location.origin}/api/race/distances`;
    try {
        await navigator.clipboard.writeText(url);
        showToast('DISTANCE API COPIED');
    } catch (_) {
        showToast(url);
    }
});

window.addEventListener('beforeunload', () => clearTimeout(state.pollTimer));
bootstrap();

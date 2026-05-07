const FormUx = (() => {
  const languageOptions = ['English', 'Hindi', 'Spanish', 'French', 'German', 'Japanese', 'Chinese', 'Arabic', 'Russian'];
  const expertiseSuggestions = [
    'Java', 'JavaScript', 'TypeScript', 'Python', 'C++', 'C#', 'DSA', 'System Design',
    'Spring Boot', 'React', 'Node.js', 'SQL', 'AWS', 'Docker', 'Kubernetes',
    'Machine Learning', 'ChatGPT', 'Frontend', 'Backend', 'Behavioral'
  ];

  const canonicalTerms = new Map([...languageOptions, ...expertiseSuggestions].map(item => [item.toLowerCase(), item]));
  canonicalTerms.set('c sharp', 'C#');
  canonicalTerms.set('csharp', 'C#');
  canonicalTerms.set('cpp', 'C++');
  canonicalTerms.set('c plus plus', 'C++');
  canonicalTerms.set('nodejs', 'Node.js');
  canonicalTerms.set('node js', 'Node.js');

  function splitValues(value) {
    if (Array.isArray(value)) return value;
    return String(value || '').split(',').map(item => item.trim()).filter(Boolean);
  }

  function normalizeValue(value) {
    const compact = String(value || '').replace(/\s+/g, ' ').trim().replace(/^,+|,+$/g, '');
    if (!compact) return '';
    const canonical = canonicalTerms.get(compact.toLowerCase());
    if (canonical) return canonical;
    return compact.split(' ').map(part => {
      if (/^[A-Z0-9+#.]{2,}$/.test(part)) return part;
      if (/^(api|aws|dsa|sql|ui|ux|ai|ml)$/i.test(part)) return part.toUpperCase();
      return part.charAt(0).toUpperCase() + part.slice(1);
    }).join(' ');
  }

  function uniqueValues(values) {
    const seen = new Set();
    return splitValues(values).map(normalizeValue).filter(value => {
      const key = value.toLowerCase();
      if (!value || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  function initTagInput(inputOrId, options = {}) {
    const input = resolveInput(inputOrId);
    if (!input || input.dataset.enhancedControl === 'tag') return input?.__chipControl;

    const suggestions = options.suggestions || expertiseSuggestions;
    const control = createControl(input, {
      className: 'tag-input-control',
      placeholder: options.placeholder || input.placeholder || 'Add expertise',
      label: options.label || 'Add expertise',
    });
    const state = { values: uniqueValues(input.value), query: '', activeIndex: -1, open: false };

    input.type = 'hidden';
    input.dataset.enhancedControl = 'tag';
    input.classList.add('enhanced-source');

    function filteredSuggestions() {
      const query = state.query.toLowerCase();
      return suggestions
        .map(normalizeValue)
        .filter(Boolean)
        .filter(item => !hasValue(state.values, item))
        .filter(item => !query || item.toLowerCase().includes(query))
        .slice(0, 8);
    }

    function commit(raw) {
      const value = normalizeValue(raw);
      if (!value || hasValue(state.values, value)) {
        control.textInput.value = '';
        state.query = '';
        render();
        return;
      }
      state.values.push(value);
      control.textInput.value = '';
      state.query = '';
      state.activeIndex = -1;
      sync(input, state.values);
      render();
    }

    function removeAt(index) {
      state.values.splice(index, 1);
      sync(input, state.values);
      render();
      control.textInput.focus();
    }

    function render() {
      renderChips(control.chipWrap, control.textInput, state.values, removeAt);
      renderOptions(control, filteredSuggestions(), state, options.suggestionClickCommits === false ? fillSuggestion : commit);
    }

    function fillSuggestion(raw) {
      const value = normalizeValue(raw);
      control.textInput.value = value;
      state.query = value;
      state.open = false;
      state.activeIndex = -1;
      render();
      control.textInput.focus();
    }

    control.textInput.addEventListener('focus', () => {
      state.open = true;
      render();
    });

    control.textInput.addEventListener('input', () => {
      state.query = control.textInput.value;
      state.activeIndex = -1;
      state.open = true;
      render();
    });

    control.textInput.addEventListener('keydown', event => {
      const items = filteredSuggestions();
      if (event.key === 'Enter' || event.key === ',' || (options.commitOnTab !== false && event.key === 'Tab' && control.textInput.value.trim())) {
        event.preventDefault();
        commit(state.activeIndex >= 0 && items[state.activeIndex] ? items[state.activeIndex] : control.textInput.value);
      } else if (event.key === 'Backspace' && !control.textInput.value && state.values.length) {
        removeAt(state.values.length - 1);
      } else if (event.key === 'ArrowDown' && items.length) {
        event.preventDefault();
        state.open = true;
        state.activeIndex = (state.activeIndex + 1) % items.length;
        render();
      } else if (event.key === 'ArrowUp' && items.length) {
        event.preventDefault();
        state.open = true;
        state.activeIndex = state.activeIndex <= 0 ? items.length - 1 : state.activeIndex - 1;
        render();
      } else if (event.key === 'Escape') {
        state.open = false;
        state.activeIndex = -1;
        render();
      }
    });

    control.chipWrap.addEventListener('click', () => control.textInput.focus());
    addOutsideClose(control.shell, state, render);

    input.__chipControl = {
      values: () => [...state.values],
      value: () => state.values.join(', '),
      setValues(values) {
        state.values = uniqueValues(values);
        sync(input, state.values);
        render();
      },
    };
    sync(input, state.values);
    render();
    return input.__chipControl;
  }

  function initLanguageSelect(inputOrId, options = {}) {
    const input = resolveInput(inputOrId);
    if (!input || input.dataset.enhancedControl === 'language') return input?.__languageControl;

    const allOptions = options.options || languageOptions;
    const control = createControl(input, {
      className: 'language-select-control',
      placeholder: options.placeholder || 'Search languages',
      label: options.label || 'Search languages',
    });
    const state = { values: uniqueValues(input.value), query: '', activeIndex: -1, open: false };

    input.type = 'hidden';
    input.dataset.enhancedControl = 'language';
    input.classList.add('enhanced-source');

    function filteredOptions() {
      const query = state.query.toLowerCase();
      return allOptions
        .map(normalizeValue)
        .filter(item => !hasValue(state.values, item))
        .filter(item => !query || item.toLowerCase().includes(query));
    }

    function select(raw) {
      const value = normalizeValue(raw);
      if (!value || hasValue(state.values, value)) return;
      state.values.push(value);
      control.textInput.value = '';
      state.query = '';
      state.activeIndex = -1;
      state.open = false;
      sync(input, state.values);
      render();
      control.textInput.focus();
    }

    function removeAt(index) {
      state.values.splice(index, 1);
      sync(input, state.values);
      render();
      control.textInput.focus();
    }

    function render() {
      renderChips(control.chipWrap, control.textInput, state.values, removeAt);
      renderOptions(control, filteredOptions(), state, select);
    }

    control.textInput.addEventListener('focus', () => {
      state.open = true;
      render();
    });

    control.textInput.addEventListener('input', () => {
      state.query = control.textInput.value;
      state.activeIndex = -1;
      state.open = true;
      render();
    });

    control.textInput.addEventListener('keydown', event => {
      const items = filteredOptions();
      if (event.key === 'Enter' && items.length) {
        event.preventDefault();
        select(items[Math.max(0, state.activeIndex)]);
      } else if (event.key === 'Backspace' && !control.textInput.value && state.values.length) {
        removeAt(state.values.length - 1);
      } else if (event.key === 'ArrowDown' && items.length) {
        event.preventDefault();
        state.open = true;
        state.activeIndex = (state.activeIndex + 1) % items.length;
        render();
      } else if (event.key === 'ArrowUp' && items.length) {
        event.preventDefault();
        state.open = true;
        state.activeIndex = state.activeIndex <= 0 ? items.length - 1 : state.activeIndex - 1;
        render();
      } else if (event.key === 'Escape') {
        state.open = false;
        state.activeIndex = -1;
        render();
      }
    });

    control.chipWrap.addEventListener('click', () => control.textInput.focus());
    addOutsideClose(control.shell, state, render);

    input.__languageControl = {
      values: () => [...state.values],
      value: () => state.values.join(', '),
      setValues(values) {
        state.values = uniqueValues(values);
        sync(input, state.values);
        render();
      },
    };
    sync(input, state.values);
    render();
    return input.__languageControl;
  }

  function createControl(input, config) {
    const shell = document.createElement('div');
    const chipWrap = document.createElement('div');
    const textInput = document.createElement('input');
    const list = document.createElement('div');

    shell.className = `chip-combobox ${config.className}`;
    chipWrap.className = 'chip-input-shell';
    textInput.type = 'text';
    textInput.className = 'chip-text-input';
    textInput.placeholder = config.placeholder;
    textInput.setAttribute('aria-label', config.label);
    textInput.setAttribute('autocomplete', 'off');
    textInput.setAttribute('role', 'combobox');
    textInput.setAttribute('aria-expanded', 'false');
    list.className = 'chip-suggestions';
    list.setAttribute('role', 'listbox');

    chipWrap.appendChild(textInput);
    shell.append(chipWrap, list);
    input.insertAdjacentElement('afterend', shell);
    return { shell, chipWrap, textInput, list };
  }

  function renderChips(chipWrap, textInput, values, removeAt) {
    chipWrap.querySelectorAll('.input-chip').forEach(item => item.remove());
    values.forEach((value, index) => chipWrap.insertBefore(chip(value, () => removeAt(index)), textInput));
  }

  function renderOptions(control, options, state, onSelect) {
    const shouldOpen = state.open && options.length > 0;
    control.shell.classList.toggle('open', shouldOpen);
    control.textInput.setAttribute('aria-expanded', String(shouldOpen));
    control.list.replaceChildren();
    if (!shouldOpen) return;
    state.activeIndex = Math.max(-1, Math.min(state.activeIndex, options.length - 1));
    options.forEach((item, index) => {
      const option = document.createElement('button');
      option.type = 'button';
      option.className = `chip-suggestion${index === state.activeIndex ? ' active' : ''}`;
      option.setAttribute('role', 'option');
      option.textContent = item;
      option.addEventListener('mousedown', event => {
        event.preventDefault();
        onSelect(item);
      });
      control.list.appendChild(option);
    });
  }

  function addOutsideClose(shell, state, render) {
    document.addEventListener('pointerdown', event => {
      if (shell.contains(event.target)) return;
      state.open = false;
      state.activeIndex = -1;
      render();
    });
  }

  function sync(input, values) {
    input.value = values.join(', ');
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  function hasValue(values, value) {
    return values.some(item => item.toLowerCase() === value.toLowerCase());
  }

  function chip(label, onRemove) {
    const item = document.createElement('span');
    const text = document.createElement('span');
    const button = document.createElement('button');
    item.className = 'input-chip';
    text.textContent = label;
    button.type = 'button';
    button.className = 'chip-remove';
    button.setAttribute('aria-label', `Remove ${label}`);
    button.textContent = '×';
    button.addEventListener('click', onRemove);
    item.append(text, button);
    return item;
  }

  function resolveInput(inputOrId) {
    return typeof inputOrId === 'string' ? document.getElementById(inputOrId) : inputOrId;
  }

  function initPasswordToggles(root = document) {
    root.querySelectorAll('input[type="password"], input[data-password-toggle="true"]').forEach(input => {
      if (input.dataset.passwordToggleReady === 'true') return;
      input.dataset.passwordToggleReady = 'true';
      input.dataset.passwordToggle = 'true';
      const wrapper = document.createElement('div');
      wrapper.className = 'password-field';
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'password-toggle';
      button.setAttribute('aria-label', `Show ${input.labels?.[0]?.textContent || 'password'}`);
      button.setAttribute('aria-pressed', 'false');
      button.innerHTML = eyeIcon(false);
      input.insertAdjacentElement('beforebegin', wrapper);
      wrapper.append(input, button);
      button.addEventListener('click', () => {
        const isVisible = input.type === 'text';
        input.type = isVisible ? 'password' : 'text';
        button.setAttribute('aria-pressed', String(!isVisible));
        button.setAttribute('aria-label', `${isVisible ? 'Show' : 'Hide'} ${input.labels?.[0]?.textContent || 'password'}`);
        button.innerHTML = eyeIcon(!isVisible);
        input.focus();
      });
    });
  }

  function eyeIcon(active) {
    return active
      ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12s3.3-6 9-6 9 6 9 6-3.3 6-9 6-9-6-9-6Z"/><path d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"/><path d="m4 20 16-16"/></svg>'
      : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12s3.3-6 9-6 9 6 9 6-3.3 6-9 6-9-6-9-6Z"/><path d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"/></svg>';
  }

  return {
    initTagInput,
    initLanguageSelect,
    initPasswordToggles,
    normalizeValues: uniqueValues,
    getTagValues(id) {
      const input = resolveInput(id);
      return input?.__chipControl?.values() || uniqueValues(input?.value);
    },
    getLanguageValues(id) {
      const input = resolveInput(id);
      return input?.__languageControl?.values() || uniqueValues(input?.value);
    },
    getLanguageString(id) {
      return this.getLanguageValues(id).join(', ');
    },
  };
})();

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
    const state = { values: uniqueValues(input.value), query: '', activeIndex: -1, open: false };
    const shell = document.createElement('div');
    const chipWrap = document.createElement('div');
    const textInput = document.createElement('input');
    const list = document.createElement('div');

    input.type = 'hidden';
    input.dataset.enhancedControl = 'tag';
    input.classList.add('enhanced-source');

    shell.className = 'chip-combobox tag-input-control';
    chipWrap.className = 'chip-input-shell';
    textInput.type = 'text';
    textInput.className = 'chip-text-input';
    textInput.placeholder = options.placeholder || input.placeholder || 'Add expertise';
    textInput.setAttribute('aria-label', options.label || 'Add expertise');
    textInput.setAttribute('autocomplete', 'off');
    list.className = 'chip-suggestions';
    list.setAttribute('role', 'listbox');

    shell.append(chipWrap, list);
    input.insertAdjacentElement('afterend', shell);

    function commit(raw) {
      const value = normalizeValue(raw);
      if (!value || state.values.some(item => item.toLowerCase() === value.toLowerCase())) return;
      state.values.push(value);
      textInput.value = '';
      state.query = '';
      state.activeIndex = -1;
      sync();
      render();
    }

    function removeAt(index) {
      state.values.splice(index, 1);
      sync();
      render();
      textInput.focus();
    }

    function filteredSuggestions() {
      const query = state.query.toLowerCase();
      return suggestions
        .map(normalizeValue)
        .filter(Boolean)
        .filter(item => !state.values.some(value => value.toLowerCase() === item.toLowerCase()))
        .filter(item => !query || item.toLowerCase().includes(query))
        .slice(0, 8);
    }

    function sync() {
      input.value = state.values.join(', ');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }

    function render() {
      chipWrap.replaceChildren();
      state.values.forEach((value, index) => chipWrap.appendChild(chip(value, () => removeAt(index))));
      chipWrap.appendChild(textInput);

      const items = filteredSuggestions();
      state.open = document.activeElement === textInput && (items.length > 0 || textInput.value.trim().length > 0);
      shell.classList.toggle('open', state.open);
      list.replaceChildren();
      if (!state.open) return;
      state.activeIndex = Math.min(state.activeIndex, items.length - 1);
      items.forEach((item, index) => {
        const option = document.createElement('button');
        option.type = 'button';
        option.className = `chip-suggestion${index === state.activeIndex ? ' active' : ''}`;
        option.setAttribute('role', 'option');
        option.textContent = item;
        option.addEventListener('mousedown', event => {
          event.preventDefault();
          commit(item);
        });
        list.appendChild(option);
      });
    }

    textInput.addEventListener('input', () => {
      state.query = textInput.value;
      state.activeIndex = -1;
      render();
    });

    textInput.addEventListener('keydown', event => {
      const items = filteredSuggestions();
      if (event.key === 'Enter' || event.key === ',') {
        event.preventDefault();
        commit(state.activeIndex >= 0 ? items[state.activeIndex] : textInput.value);
      } else if (event.key === 'Backspace' && !textInput.value && state.values.length) {
        removeAt(state.values.length - 1);
      } else if (event.key === 'ArrowDown' && items.length) {
        event.preventDefault();
        state.activeIndex = (state.activeIndex + 1) % items.length;
        render();
      } else if (event.key === 'ArrowUp' && items.length) {
        event.preventDefault();
        state.activeIndex = state.activeIndex <= 0 ? items.length - 1 : state.activeIndex - 1;
        render();
      } else if (event.key === 'Escape') {
        state.open = false;
        textInput.blur();
        render();
      }
    });

    textInput.addEventListener('blur', () => {
      if (textInput.value.trim()) commit(textInput.value);
      setTimeout(render, 120);
    });
    chipWrap.addEventListener('click', () => textInput.focus());

    input.__chipControl = {
      values: () => [...state.values],
      value: () => state.values.join(', '),
      setValues(values) {
        state.values = uniqueValues(values);
        sync();
        render();
      },
    };
    sync();
    render();
    return input.__chipControl;
  }

  function initLanguageSelect(inputOrId, options = {}) {
    const input = resolveInput(inputOrId);
    if (!input || input.dataset.enhancedControl === 'language') return input?.__languageControl;

    const allOptions = options.options || languageOptions;
    const state = { values: uniqueValues(input.value), query: '', activeIndex: -1, open: false };
    const shell = document.createElement('div');
    const chipWrap = document.createElement('div');
    const search = document.createElement('input');
    const list = document.createElement('div');

    input.type = 'hidden';
    input.dataset.enhancedControl = 'language';
    input.classList.add('enhanced-source');

    shell.className = 'chip-combobox language-select-control';
    chipWrap.className = 'chip-input-shell';
    search.type = 'text';
    search.className = 'chip-text-input';
    search.placeholder = options.placeholder || 'Search languages';
    search.setAttribute('aria-label', options.label || 'Search languages');
    search.setAttribute('autocomplete', 'off');
    list.className = 'chip-suggestions';
    list.setAttribute('role', 'listbox');
    shell.append(chipWrap, list);
    input.insertAdjacentElement('afterend', shell);

    function filteredOptions() {
      const query = state.query.toLowerCase();
      return allOptions
        .map(normalizeValue)
        .filter(item => !state.values.some(value => value.toLowerCase() === item.toLowerCase()))
        .filter(item => !query || item.toLowerCase().includes(query));
    }

    function select(raw) {
      const value = normalizeValue(raw);
      if (!value || state.values.some(item => item.toLowerCase() === value.toLowerCase())) return;
      state.values.push(value);
      search.value = '';
      state.query = '';
      state.activeIndex = -1;
      sync();
      render();
    }

    function removeAt(index) {
      state.values.splice(index, 1);
      sync();
      render();
      search.focus();
    }

    function sync() {
      input.value = state.values.join(', ');
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }

    function render() {
      chipWrap.replaceChildren();
      state.values.forEach((value, index) => chipWrap.appendChild(chip(value, () => removeAt(index))));
      chipWrap.appendChild(search);

      const items = filteredOptions();
      state.open = document.activeElement === search && items.length > 0;
      shell.classList.toggle('open', state.open);
      list.replaceChildren();
      if (!state.open) return;
      state.activeIndex = Math.min(state.activeIndex, items.length - 1);
      items.forEach((item, index) => {
        const option = document.createElement('button');
        option.type = 'button';
        option.className = `chip-suggestion${index === state.activeIndex ? ' active' : ''}`;
        option.setAttribute('role', 'option');
        option.textContent = item;
        option.addEventListener('mousedown', event => {
          event.preventDefault();
          select(item);
        });
        list.appendChild(option);
      });
    }

    search.addEventListener('input', () => {
      state.query = search.value;
      state.activeIndex = -1;
      render();
    });

    search.addEventListener('keydown', event => {
      const items = filteredOptions();
      if (event.key === 'Enter' && items.length) {
        event.preventDefault();
        select(items[Math.max(0, state.activeIndex)]);
      } else if (event.key === 'Backspace' && !search.value && state.values.length) {
        removeAt(state.values.length - 1);
      } else if (event.key === 'ArrowDown' && items.length) {
        event.preventDefault();
        state.activeIndex = (state.activeIndex + 1) % items.length;
        render();
      } else if (event.key === 'ArrowUp' && items.length) {
        event.preventDefault();
        state.activeIndex = state.activeIndex <= 0 ? items.length - 1 : state.activeIndex - 1;
        render();
      } else if (event.key === 'Escape') {
        state.open = false;
        search.blur();
        render();
      }
    });

    search.addEventListener('focus', render);
    search.addEventListener('blur', () => setTimeout(render, 120));
    chipWrap.addEventListener('click', () => search.focus());

    input.__languageControl = {
      values: () => [...state.values],
      value: () => state.values.join(', '),
      setValues(values) {
        state.values = uniqueValues(values);
        sync();
        render();
      },
    };
    sync();
    render();
    return input.__languageControl;
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

  return {
    initTagInput,
    initLanguageSelect,
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

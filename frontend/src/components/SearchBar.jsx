export function SearchBar({ value, onChange, placeholder, label }) {
  return (
    <div>
      <label className="visually-hidden" htmlFor="twin-search">
        {label}
      </label>
      <input
        id="twin-search"
        className="input"
        type="search"
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}

export function TypeFilter({ options, selected, onToggle, label }) {
  return (
    <div role="group" aria-label={label} className="controls">
      {options.map((option) => {
        const active = selected.includes(option);
        return (
          <button
            key={option}
            type="button"
            className="chip"
            aria-pressed={active}
            onClick={() => onToggle(option)}
          >
            {option}
          </button>
        );
      })}
    </div>
  );
}

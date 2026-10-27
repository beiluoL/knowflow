export type SelectAtom = string | number | null;
export type SelectValue = SelectAtom | SelectAtom[];
/** Keys remain configurable for compatibility with existing API view models. */
export interface SelectOption {
  [key: string]: any;
}
export interface SelectChoice {
  value: SelectAtom;
  key: string;
  label: string;
  group: string;
  disabled: boolean;
  description?: string;
  icon?: string;
  color?: string;
  source: SelectOption;
}
export function normalizeChoices(
  options: readonly SelectOption[],
  labelKey = "label",
  valueKey = "value",
  groupKey = "group",
): SelectChoice[] {
  const seen = new Set<string>();
  return options.flatMap((source) => {
    const value = source[valueKey];
    if (
      value !== null &&
      typeof value !== "string" &&
      typeof value !== "number"
    )
      return [];
    const key = typeof value + ":" + String(value);
    if (seen.has(key)) return [];
    seen.add(key);
    return [
      {
        value,
        key,
        label: String(source[labelKey] ?? value ?? ""),
        group: String(source[groupKey] ?? ""),
        disabled: !!source.disabled,
        description: source.description,
        icon: source.icon,
        color: source.color,
        source,
      },
    ];
  });
}
export function uniqueValues(values: readonly SelectAtom[]): SelectAtom[] {
  return [...new Set(values)];
}

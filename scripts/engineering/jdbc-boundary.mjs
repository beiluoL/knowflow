/** Scoped regression guard, not a global JDBC prohibition or SQL safety validator. */
export function jdbcBoundaryViolations(file, source) {
  if (file.replaceAll('\\', '/') !== 'backend/src/main/java/com/knowflow/techenglish/service/TechVocabService.java') return []
  const code = source.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g, value => value.replace(/[^\n]/g, ' '))
  return /\b(?:JdbcTemplate|NamedParameterJdbcTemplate|JdbcOperations|NamedParameterJdbcOperations)\b/.test(code)
    ? ['TechVocabService must keep its governed mapper-only data access boundary'] : []
}

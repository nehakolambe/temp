@Component
public class SqlScriptRunner {

    private final DataSource dataSource;

    public SqlScriptRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String runScript(String scriptPath) {
        Resource resource = new ClassPathResource(scriptPath);
        StringBuilder result = new StringBuilder();

        try (Connection connection = dataSource.getConnection();
             BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(resource, "UTF-8"), true, false, "--", ";", "/*", "*/",
                    new StatementCallback<Boolean>() {
                        @Override
                        public Boolean doInStatement(Statement statement) throws SQLException, DataAccessException {
                            boolean hasResult = false;
                            boolean isFirst = true;
                            ResultSet resultSet = null;
                            try {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    line = line.trim();
                                    if (line.startsWith("--") || line.isEmpty()) {
                                        continue;
                                    }
                                    if (isFirst && line.startsWith("SELECT")) {
                                        hasResult = true;
                                        resultSet = statement.executeQuery(line);
                                        ResultSetMetaData metaData = resultSet.getMetaData();
                                        int columnCount = metaData.getColumnCount();
                                        for (int i = 1; i <= columnCount; i++) {
                                            result.append(metaData.getColumnName(i));
                                            if (i < columnCount) {
                                                result.append(", ");
                                            }
                                        }
                                        result.append(System.lineSeparator());
                                    } else if (hasResult) {
                                        resultSet = statement.executeQuery(line);
                                        while (resultSet.next()) {
                                            ResultSetMetaData metaData = resultSet.getMetaData();
                                            int columnCount = metaData.getColumnCount();
                                            for (int i = 1; i <= columnCount; i++) {
                                                result.append(resultSet.getString(i));
                                                if (i < columnCount) {
                                                    result.append(", ");
                                                }
                                            }
                                            result.append(System.lineSeparator());
                                        }
                                    } else {
                                        statement.execute(line);
                                    }
                                    isFirst = false;
                                }
                            } finally {
                                JdbcUtils.closeResultSet(resultSet);
                            }
                            return true;
                        }
                    });
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Failed to execute SQL script", e);
        }

        return result.toString();
    }
}

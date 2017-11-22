package.de.metas.jenkins

def call(final String mvnRepoBaseURL, String mvnRepoName)
{
	withCredentials([usernameColonPassword(credentialsId: 'nexus_jenkins', variable: 'NEXUS_LOGIN')])
	{
		if(!isRepoExists(mvnRepoBaseURL, mvnRepoName))
		{
			createRepo(mvnRepoBaseURL, mvnRepoName);
		}
	}
}

boolean isRepoExists(final String mvnRepoBaseURL, String mvnRepoName)
{
	echo "Check if the nexus repository ${mvnRepoName} exists";

	// check if there is a repository for ur branch
	final String checkForRepoCommand = "curl --silent -X GET -u ${NEXUS_LOGIN} ${mvnRepoBaseURL}/service/local/repositories | grep '<id>${mvnRepoName}-releases</id>'";
	final grepExitCode = sh returnStatus: true, script: checkForRepoCommand;
	final repoExists = grepExitCode == 0;

	echo "The nexus repository ${mvnRepoName} exists: ${repoExists}";
	return repoExists;
}

void createRepo(final String mvnRepoBaseURL, final String mvnRepoName)
{
		echo "Create the repository ${mvnRepoName}-releases";
		final String createHostedRepoPayload = """<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<repository>
  <data>
		<id>${mvnRepoName}-releases</id>
		<name>${mvnRepoName}-releases</name>
		<exposed>true</exposed>
		<repoType>hosted</repoType>
		<writePolicy>ALLOW_WRITE_ONCE</writePolicy>
		<browseable>true</browseable>
		<indexable>true</indexable>
		<repoPolicy>RELEASE</repoPolicy>
		<providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>
		<provider>maven2</provider>
		<format>maven2</format>
  </data>
</repository>
""";

		// # nexus ignored application/json
		final String createHostedRepoCommand =  "curl --silent -H \"Content-Type: application/xml\" -X POST -u ${NEXUS_LOGIN} -d \'${createHostedRepoPayload}\' ${mvnRepoBaseURL}/service/local/repositories"
		sh "${createHostedRepoCommand}"

		final String PUBLIC_REPO_BASE_URL="https://repo.metasfresh.com";

		// create a repo group that contains both the 3rd-party stuff and the locally build artifacts
		// this reposity will be used by the build
		echo "Create the repository-group ${mvnRepoName}";
		final String createGroupPayload = """<?xml version="1.0" encoding="UTF-8"?>
<repo-group>
  <data>
    <repositories>

      <!-- include mvn-public that contains everything we need to perform the build -->
      <repo-group-member>
        <name>mvn-public</name>
        <id>mvn-public</id>
        <resourceURI>${mvnRepoBaseURL}/content/repositories/mvn-public/</resourceURI>
			</repo-group-member>

			<!-- include ${mvnRepoName}-releases which is the repo to which we deploy everything we build within this branch -->
      <repo-group-member>
        <name>${mvnRepoName}-releases</name>
        <id>${mvnRepoName}-releases</id>
        <resourceURI>${mvnRepoBaseURL}/content/repositories/${mvnRepoName}-releases/</resourceURI>
      </repo-group-member>

    </repositories>
    <name>${mvnRepoName}</name>
    <repoType>group</repoType>
    <providerRole>org.sonatype.nexus.proxy.repository.Repository</providerRole>
    <exposed>true</exposed>
    <id>${mvnRepoName}</id>
	<provider>maven2</provider>
	<format>maven2</format>
  </data>
</repo-group>
"""

		// # nexus ignored application/json
		final String createGroupCommand =  "curl --silent -H \"Content-Type: application/xml\" -X POST -u ${NEXUS_LOGIN} -d \'${createGroupPayload}\' ${mvnRepoBaseURL}/service/local/repo_groups"
		sh "${createGroupCommand}"

		echo "Create the scheduled task to keep ${mvnRepoName}-releases from growing too big";
		final String createSchedulePayload = """<?xml version="1.0" encoding="UTF-8"?>
<scheduled-task>
  <data>
	<id>cleanup-repo-${mvnRepoName}-releases</id>
	<enabled>true</enabled>
	<name>Remove Releases from ${mvnRepoName}-releases</name>
	<typeId>ReleaseRemoverTask</typeId>
	<schedule>daily</schedule>
	<startDate>${currentBuild.startTimeInMillis}</startDate>
	<recurringTime>03:00</recurringTime>
	<properties>
      <scheduled-task-property>
        <key>numberOfVersionsToKeep</key>
        <value>3</value>
      </scheduled-task-property>
      <scheduled-task-property>
        <key>indexBackend</key>
        <value>false</value>
      </scheduled-task-property>
      <scheduled-task-property>
        <key>repositoryId</key>
        <value>${mvnRepoName}-releases</value>
      </scheduled-task-property>
	</properties>
  </data>
</scheduled-task>"""

		// # nexus ignored application/json
		final String createScheduleCommand =  "curl --silent -H \"Content-Type: application/xml\" -X POST -u ${NEXUS_LOGIN} -d \'${createSchedulePayload}\' ${mvnRepoBaseURL}/service/local/schedules"
		sh "${createScheduleCommand}"
}

def deleteRepo(final String mvnRepoBaseURL, String mvnRepoName)
{
		echo "Delete the repository ${mvnRepoName}";

		final String deleteGroupCommand = "curl --silent -X DELETE -u ${NEXUS_LOGIN} ${mvnRepoBaseURL}/service/local/repo_groups/${mvnRepoName}"
		sh "${deleteGroupCommand}"

		final String deleteRepoCommand = "curl --silent -X DELETE -u ${NEXUS_LOGIN} ${mvnRepoBaseURL}/service/local/repositories/${mvnRepoName}-releases"
		sh "${deleteRepoCommand}"

		final String deleteScheduleCommand = "curl --silent -X DELETE -u ${NEXUS_LOGIN} ${mvnRepoBaseURL}/service/local/schedules/cleanup-repo-${mvnRepoName}-releases"
		sh "${deleteScheduleCommand}"
}

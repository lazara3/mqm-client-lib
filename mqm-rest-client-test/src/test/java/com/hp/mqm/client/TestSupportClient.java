// (C) Copyright 2003-2015 Hewlett-Packard Development Company, L.P.

package com.hp.mqm.client;

import com.hp.mqm.client.model.PagedList;
import com.hp.mqm.client.model.Release;
import com.hp.mqm.client.model.Taxonomy;
import com.hp.mqm.client.model.TaxonomyType;
import com.hp.mqm.client.model.TestRun;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class TestSupportClient extends AbstractMqmRestClient {

    private static final String WORKSPACE_FRAGMENT= "?workspace-id={workspace}";

    private static final String URI_RELEASES = "releases";
    private static final String URI_TEST_RUN = "test-runs" + WORKSPACE_FRAGMENT;
    private static final String URI_TAXONOMY_TYPES = "taxonomy-types" + WORKSPACE_FRAGMENT;
    private static final String URI_TAXONOMIES = "taxonomies" + WORKSPACE_FRAGMENT;
    private static final String URI_CI_SERVERS = "ci-servers" + WORKSPACE_FRAGMENT;
    private static final String URI_CI_JOBS = "ci-jobs" + WORKSPACE_FRAGMENT;
    private static final String URI_BUILDS = "builds" + WORKSPACE_FRAGMENT;

    protected TestSupportClient(MqmConnectionConfig connectionConfig) {
        super(connectionConfig);
    }

    public Release createRelease(String name, long workspaceId) throws IOException {
        JSONObject releaseObject = ResourceUtils.readJson("release.json");
        releaseObject.put("name", name);

        JSONObject resultObject = postEntity(URI_RELEASES, workspaceId, releaseObject);
        return new Release(resultObject.getLong("id"), resultObject.getString("name"));
    }

    public TaxonomyType createTaxonomyType(String name, long workspaceId) throws IOException {
        JSONObject taxonomyTypeObject = ResourceUtils.readJson("taxonomyType.json");
        taxonomyTypeObject.put("name", name);

        JSONObject resultObject = postEntity(URI_TAXONOMY_TYPES, workspaceId, taxonomyTypeObject);
        return new TaxonomyType(resultObject.getLong("id"), resultObject.getString("name"));
    }

    public Taxonomy createTaxonomy(Long typeId, String name, long workspaceId) throws IOException {
        JSONObject taxonomyObject = ResourceUtils.readJson("taxonomy.json");
        taxonomyObject.put("taxonomy-type-id", typeId);
        taxonomyObject.put("name", name);

        JSONObject resultObject = postEntity(URI_TAXONOMIES, workspaceId, taxonomyObject);
        return new Taxonomy(resultObject.getLong("id"), resultObject.getLong("taxonomy-type-id"), resultObject.getString("name"), null);
    }

    public PagedList<TestRun> queryTestRuns(String name, long workspaceId, int offset, int limit) {
        List<String> conditions = new LinkedList<String>();
        if (!StringUtils.isEmpty(name)) {
            conditions.add(condition("name", "*" + name + "*"));
        }
        return getEntities(getEntityURI(URI_TEST_RUN, conditions, workspaceId, offset, limit), offset, new TestRunEntityFactory());
    }

    private JSONObject postEntity(String uri, long workspace, JSONObject entityObject) throws IOException {
        HttpPost request = new HttpPost(createWorkspaceApiUri(uri, workspace));
        JSONArray data = new JSONArray();
        data.add(entityObject);
        JSONObject body = new JSONObject();
        body.put("data", data);
        request.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
        HttpResponse response = null;
        try {
            response = execute(request);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_CREATED) {
                String payload = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
                throw new IOException("Posting failed with status code " + response.getStatusLine().getStatusCode() + ", reason " + response.getStatusLine().getReasonPhrase() + " and payload: " + payload);
            }
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            response.getEntity().writeTo(result);
            JSONObject jsonObject = JSONObject.fromObject(new String(result.toByteArray(), "UTF-8"));
            return jsonObject.getJSONArray("data").getJSONObject(0);
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    public boolean checkBuild(String serverIdentity, String jobName, int number) {
        List<String> serverConditions = new LinkedList<String>();
        serverConditions.add(condition("identity", serverIdentity));
        PagedList<JSONObject> servers = getEntities(getEntityURI(URI_CI_SERVERS, serverConditions, null, 0, 1), 0, new JsonEntityFactory());
        if (servers.getItems().isEmpty()) {
            return false;
        }

        List<String> jobConditions = new LinkedList<String>();
        jobConditions.add(condition("server-id", String.valueOf(servers.getItems().get(0).getInt("id"))));
        jobConditions.add(condition("name", jobName));
        PagedList<JSONObject> jobs = getEntities(getEntityURI(URI_CI_JOBS, jobConditions, null, 0, 1), 0, new JsonEntityFactory());
        if(jobs.getItems().isEmpty()) {
            return false;
        }

        List<String> buildConditions = new LinkedList<String>();
        buildConditions.add(condition("ci-job-id", String.valueOf(jobs.getItems().get(0).getInt("id"))));
        buildConditions.add(condition("name", String.valueOf(number)));
        PagedList<JSONObject> builds = getEntities(getEntityURI(URI_BUILDS, buildConditions, null, 0, 1), 0, new JsonEntityFactory());
        return !builds.getItems().isEmpty();
    }

    private static class TestRunEntityFactory implements EntityFactory<TestRun> {

        @Override
        public TestRun create(String json) {
            JSONObject entityObject = JSONObject.fromObject(json);
            return new TestRun(
                    entityObject.getInt("id"),
                    entityObject.getString("name"));
        }
    }

    private static class JsonEntityFactory implements EntityFactory<JSONObject> {

        @Override
        public JSONObject create(String json) {
            return JSONObject.fromObject(json);
        }
    }
}
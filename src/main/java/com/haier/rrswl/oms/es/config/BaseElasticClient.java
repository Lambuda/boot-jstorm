package com.haier.rrswl.oms.es.config;

import com.alibaba.fastjson.JSON;
import com.haier.rrswl.oms.es.entity.ElasticEntity;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @description: es 操作客户端
 * @author: 01437768
 * @create: 2020/05/09 10:06
 */
@Slf4j
@Component
public class BaseElasticClient {

    @Resource
    RestHighLevelClient restHighLevelClient;

    /**
     * @param idxName 索引名称
     * @param idxSQL  索引描述
     */
    public void createIndex(String idxName, String idxSQL) {
        try {
            if (!this.indexExist(idxName)) {
                log.error(" idxName={} 已经存在,idxSql={}", idxName, idxSQL);
                return;
            }
            CreateIndexRequest request = new CreateIndexRequest(idxName);
            buildSetting(request);
            request.mapping(idxSQL, XContentType.JSON);
//            request.settings() 手工指定Setting
            CreateIndexResponse res = restHighLevelClient.indices().create(request, RequestOptions.DEFAULT);
            if (!res.isAcknowledged()) {
                throw new RuntimeException("创建索引失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * 断某个index是否存在
     *
     * @param idxName index名
     * @return boolean
     * @throws
     * @date 2019/10/17 17:27
     * @since
     */
    public boolean indexExist(String idxName) throws Exception {
        GetIndexRequest request = new GetIndexRequest(idxName);
        request.local(false);
        request.humanReadable(true);
        request.includeDefaults(false);
        request.indicesOptions(IndicesOptions.lenientExpandOpen());
        return restHighLevelClient.indices().exists(request, RequestOptions.DEFAULT);
    }

    /**
     * 断某个index是否存在
     *
     * @param idxName index名
     * @return boolean
     * @throws
     * @date 2019/10/17 17:27
     * @since
     */
    public boolean isExistsIndex(String idxName) throws Exception {
        return restHighLevelClient.indices().exists(new GetIndexRequest(idxName), RequestOptions.DEFAULT);
    }


    /**
     * 设置分片
     *
     * @param request
     * @return void
     * @throws
     * @date 2019/10/17 19:27
     * @since
     */
    public void buildSetting(CreateIndexRequest request) {
        request.settings(Settings.builder().put("index.number_of_shards", 3)
                .put("index.number_of_replicas", 2));
    }

    /**
     * @param idxName index
     * @param entity  对象
     * @return void
     * @throws
     * @date 2019/10/17 17:27
     * @since
     */
    public void insertOrUpdateOne(String idxName, ElasticEntity entity) {
        IndexRequest request = new IndexRequest(idxName);
        log.info("index={},id={},entity={}",idxName, entity.getId(), JSON.toJSONString(entity.getData()));
        request.id(entity.getId());
        request.source(JSON.toJSONString(entity.getData()), XContentType.JSON);
        try {
            restHighLevelClient.index(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            AtomicInteger atomicInteger;
            throw new RuntimeException(e);
        }
    }


    /**
     * 批量插入数据
     *
     * @param idxName index
     * @param list    带插入列表
     * @return void
     * @throws
     * @date 2019/10/17 17:26
     * @since
     */
    public void insertBatch(String idxName, List<ElasticEntity> list) {
        BulkRequest request = new BulkRequest();
        list.forEach(item -> request.add(new IndexRequest(idxName).id(item.getId())
                .source(item.getData(), XContentType.JSON)));
        try {
            restHighLevelClient.bulk(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 批量删除
     *
     * @param idxName index
     * @param idList  待删除列表
     * @return void
     * @throws
     * @date 2019/10/17 17:14
     * @since
     */
    public <T> void deleteBatch(String idxName, Collection<T> idList) {
        BulkRequest request = new BulkRequest();
        idList.forEach(item -> request.add(new DeleteRequest(idxName, item.toString())));
        try {
            restHighLevelClient.bulk(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param idxName index
     * @param builder 查询参数
     * @param c       结果类对象
     * @return java.util.List<T>
     * @throws
     * @date 2019/10/17 17:14
     * @since
     */
    public <T> List<T> search(String idxName, SearchSourceBuilder builder, Class<T> c) {
        SearchRequest request = new SearchRequest(idxName);
        request.source(builder);
        try {
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            SearchHit[] hits = response.getHits().getHits();
            List<T> res = new ArrayList<>(hits.length);
            for (SearchHit hit : hits) {
                res.add(JSON.parseObject(hit.getSourceAsString(), c));
            }
            return res;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除index
     *
     * @param idxName
     * @return void
     * @throws
     * @date 2019/10/17 17:13
     * @since
     */
    public void deleteIndex(String idxName) {
        try {
            if (!this.indexExist(idxName)) {
                log.error(" idxName={} 已经存在", idxName);
                return;
            }
            restHighLevelClient.indices().delete(new DeleteIndexRequest(idxName), RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * @param idxName
     * @param builder
     * @return void
     * @throws
     * @date 2019/10/17 17:13
     * @since
     */
    public void deleteByQuery(String idxName, QueryBuilder builder) {

        DeleteByQueryRequest request = new DeleteByQueryRequest(idxName);
        request.setQuery(builder);
        //设置批量操作数量,最大为10000
        request.setBatchSize(10000);
        request.setConflicts("proceed");
        try {
            restHighLevelClient.deleteByQuery(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

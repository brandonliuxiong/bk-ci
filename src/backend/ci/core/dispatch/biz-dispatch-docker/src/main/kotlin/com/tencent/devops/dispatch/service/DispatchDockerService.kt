package com.tencent.devops.dispatch.service

import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.dispatch.common.Constants
import com.tencent.devops.dispatch.dao.PipelineDockerIPInfoDao
import com.tencent.devops.dispatch.pojo.DockerHostLoadConfig
import com.tencent.devops.dispatch.pojo.DockerIpInfoVO
import com.tencent.devops.dispatch.pojo.DockerIpListPage
import com.tencent.devops.dispatch.pojo.DockerIpUpdateVO
import com.tencent.devops.dispatch.utils.CommonUtils
import com.tencent.devops.dispatch.utils.DockerHostUtils
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class DispatchDockerService @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineDockerIPInfoDao: PipelineDockerIPInfoDao,
    private val dockerHostUtils: DockerHostUtils,
    private val redisOperation: RedisOperation
) {

    companion object {
        private val logger = LoggerFactory.getLogger(DispatchDockerService::class.java)
    }

    fun list(userId: String, page: Int?, pageSize: Int?): DockerIpListPage<DockerIpInfoVO> {
        val pageNotNull = page ?: 1
        val pageSizeNotNull = pageSize ?: 10

        try {
            val dockerIpList = pipelineDockerIPInfoDao.getDockerIpList(dslContext, pageNotNull, pageSizeNotNull)
            val count = pipelineDockerIPInfoDao.getDockerIpCount(dslContext)

            if (dockerIpList.size == 0 || count == 0L) {
                return DockerIpListPage(pageNotNull, pageSizeNotNull, 0, emptyList())
            }
            val dockerIpInfoVOList = mutableListOf<DockerIpInfoVO>()
            dockerIpList.forEach {
                dockerIpInfoVOList.add(DockerIpInfoVO(
                    id = it.id,
                    dockerIp = it.dockerIp,
                    dockerHostPort = it.dockerHostPort,
                    capacity = it.capacity,
                    usedNum = it.usedNum,
                    averageCpuLoad = it.cpuLoad,
                    averageMemLoad = it.memLoad,
                    averageDiskLoad = it.diskLoad,
                    averageDiskIOLoad = it.diskIoLoad,
                    enable = it.enable,
                    grayEnv = it.grayEnv,
                    specialOn = it.specialOn,
                    createTime = it.gmtCreate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                ))
            }

            return DockerIpListPage(pageNotNull, pageSizeNotNull, count, dockerIpInfoVOList)
        } catch (e: Exception) {
            logger.error("OP dispatchDocker list error.", e)
            throw RuntimeException("OP dispatchDocker list error.")
        }
    }

    fun create(userId: String, dockerIpInfoVOs: List<DockerIpInfoVO>): Boolean {
        logger.info("$userId create docker IP $dockerIpInfoVOs")
        dockerIpInfoVOs.forEach {
            if (!CommonUtils.verifyIp(it.dockerIp.trim())) {
                logger.warn("Dispatch create dockerIp error, invalid IP format: ${it.dockerIp}")
                throw RuntimeException("Dispatch create dockerIp error, invalid IP format: ${it.dockerIp}")
            }
        }

        try {
            dockerIpInfoVOs.forEach {
                pipelineDockerIPInfoDao.createOrUpdate(
                    dslContext = dslContext,
                    dockerIp = it.dockerIp.trim(),
                    dockerHostPort = it.dockerHostPort,
                    capacity = it.capacity,
                    used = it.usedNum,
                    cpuLoad = it.averageCpuLoad,
                    memLoad = it.averageMemLoad,
                    diskLoad = it.averageDiskLoad,
                    diskIOLoad = it.averageDiskIOLoad,
                    enable = it.enable,
                    grayEnv = it.grayEnv ?: false,
                    specialOn = it.specialOn ?: false
                )
            }

            return true
        } catch (e: Exception) {
            logger.error("OP dispatchDocker create error.", e)
            throw RuntimeException("OP dispatchDocker create error.")
        }
    }

    fun update(userId: String, dockerIp: String, dockerIpUpdateVO: DockerIpUpdateVO): Boolean {
        logger.info("$userId update Docker IP: $dockerIp dockerIpUpdateVO: $dockerIpUpdateVO")
        try {
            pipelineDockerIPInfoDao.update(
                dslContext = dslContext,
                dockerIp = dockerIp,
                dockerHostPort = dockerIpUpdateVO.dockerHostPort,
                enable = dockerIpUpdateVO.enable,
                grayEnv = dockerIpUpdateVO.grayEnv,
                specialOn = dockerIpUpdateVO.specialOn
            )
            return true
        } catch (e: Exception) {
            logger.error("OP dispatchDocker update error.", e)
            throw RuntimeException("OP dispatchDocker update error.")
        }
    }

    fun updateDockerIpLoad(userId: String, dockerIp: String, dockerIpInfoVO: DockerIpInfoVO): Boolean {
        logger.info("$userId update Docker IP status enable: $dockerIp, dockerIpInfoVO: $dockerIpInfoVO")
        try {
            pipelineDockerIPInfoDao.updateDockerIpLoad(
                dslContext = dslContext,
                dockerIp = dockerIp,
                dockerHostPort = dockerIpInfoVO.dockerHostPort,
                used = dockerIpInfoVO.usedNum,
                cpuLoad = dockerIpInfoVO.averageCpuLoad,
                memLoad = dockerIpInfoVO.averageMemLoad,
                diskLoad = dockerIpInfoVO.averageDiskLoad,
                diskIOLoad = dockerIpInfoVO.averageDiskIOLoad,
                enable = dockerIpInfoVO.enable
            )
            return true
        } catch (e: Exception) {
            logger.error("OP dispatchDocker updateDockerIpEnable error.", e)
            throw RuntimeException("OP dispatchDocker updateDockerIpEnable error.")
        }
    }

    fun delete(userId: String, dockerIp: String): Boolean {
        logger.info("$userId delete Docker IP: $dockerIp")
        try {
            pipelineDockerIPInfoDao.delete(dslContext, dockerIp)
            return true
        } catch (e: Exception) {
            logger.error("OP dispatchDocker delete error.", e)
            throw RuntimeException("OP dispatchDocker delete error.")
        }
    }

    fun createDockerHostLoadConfig(
        userId: String,
        dockerHostLoadConfigMap: Map<String, DockerHostLoadConfig>
    ): Boolean {
        logger.info("$userId createDockerHostLoadConfig $dockerHostLoadConfigMap")
        if (dockerHostLoadConfigMap.size != 3) {
            throw RuntimeException("Parameter dockerHostLoadConfigMap`size is not 3.")
        }

        try {
            dockerHostUtils.createLoadConfig(dockerHostLoadConfigMap)
            return true
        } catch (e: Exception) {
            logger.error("OP dispatcheDocker create dockerhost loadConfig error.", e)
            throw RuntimeException("OP dispatcheDocker create dockerhost loadConfig error.")
        }
    }
}
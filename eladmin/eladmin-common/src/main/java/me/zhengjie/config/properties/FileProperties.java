/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.config.properties;

import lombok.Data;
import me.zhengjie.utils.ElConstant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author Zheng Jie
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /** 文件大小限制 */
    private Long maxSize;

    /** 头像大小限制 */
    private Long avatarMaxSize;

    private ElPath mac;

    private ElPath linux;

    private ElPath windows;

    public Long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Long maxSize) {
        this.maxSize = maxSize;
    }

    public Long getAvatarMaxSize() {
        return avatarMaxSize;
    }

    public void setAvatarMaxSize(Long avatarMaxSize) {
        this.avatarMaxSize = avatarMaxSize;
    }

    public ElPath getMac() {
        return mac;
    }

    public void setMac(ElPath mac) {
        this.mac = mac;
    }

    public ElPath getLinux() {
        return linux;
    }

    public void setLinux(ElPath linux) {
        this.linux = linux;
    }

    public ElPath getWindows() {
        return windows;
    }

    public void setWindows(ElPath windows) {
        this.windows = windows;
    }

    public ElPath getPath(){
        String os = System.getProperty("os.name");
        if(os.toLowerCase().startsWith(ElConstant.WIN)) {
            return windows;
        } else if(os.toLowerCase().startsWith(ElConstant.MAC)){
            return mac;
        }
        return linux;
    }

    @Data
    public static class ElPath{

        private String path;

        private String avatar;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }
    }
}

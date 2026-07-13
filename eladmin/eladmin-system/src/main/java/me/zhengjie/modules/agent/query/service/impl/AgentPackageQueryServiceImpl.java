package me.zhengjie.modules.agent.query.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.query.domain.dto.AgentPackageSpecDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentSubPackageSpecDto;
import me.zhengjie.modules.agent.query.service.AgentPackageQueryService;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.domain.SubPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.customer.pkg.mapper.SubPackageMapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

/** Agent 套餐查询实现，基于真实 parent_package_sub 关系读取。 */
@Service
@RequiredArgsConstructor
public class AgentPackageQueryServiceImpl implements AgentPackageQueryService {
    private final ParentPackageMapper parentPackageMapper;
    private final SubPackageMapper subPackageMapper;
    /** {@inheritDoc} */
    @Override public AgentPackageSpecDto getDetail(Long parentPackageId) {
        AgentPackageSpecDto dto = new AgentPackageSpecDto();
        if (parentPackageId == null || parentPackageId <= 0) return dto;
        ParentPackage parent = parentPackageMapper.selectById(parentPackageId);
        if (parent == null) return dto;
        dto.setPresent(true); dto.setParentPackageId(parent.getId()); dto.setParentPackageCode(parent.getPackageCode()); dto.setParentPackageName(parent.getPackageName()); dto.setEnabled(parent.getStatus());
        List<SubPackage> children = subPackageMapper.findByParentPackageId(parentPackageId);
        dto.setSubPackages(children.stream().map(this::sub).collect(Collectors.toList()));
        return dto;
    }
    private AgentSubPackageSpecDto sub(SubPackage source) { AgentSubPackageSpecDto dto = new AgentSubPackageSpecDto(); dto.setSubPackageId(source.getId()); dto.setSubPackageCode(source.getSubPackageCode()); dto.setSubPackageName(source.getSubPackageName()); dto.setMeatCount(source.getMeatCount()); dto.setVegCount(source.getVegCount()); dto.setIncludeSoup(source.getIncludeSoup()); dto.setIncludeRice(source.getIncludeRice()); dto.setEnabled(source.getStatus()); return dto; }
}

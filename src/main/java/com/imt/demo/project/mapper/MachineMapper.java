package com.imt.demo.project.mapper;

import com.imt.demo.project.dto.MachineDto;
import com.imt.demo.project.model.Machine;
import org.springframework.stereotype.Component;

@Component
public class MachineMapper {

    public Machine toEntity(MachineDto machineDto) {
        if (machineDto == null) {
            return null;
        }
        Machine machine = new Machine();
        machine.setId(machineDto.getId());
        machine.setName(machineDto.getName());
        machine.setEnvironmentType(machineDto.getEnvironmentType());
        machine.setHostSshPort(machineDto.getHostSshPort());
        machine.setHostSshUsername(machineDto.getHostSshUsername());
        machine.setDeploymentPort(machineDto.getDeploymentPort());
        machine.setSshHost(machineDto.getSshHost());
        return machine;
    }

    public MachineDto toDto(Machine machine) {
        if (machine == null) {
            return null;
        }
        MachineDto machineDto = new MachineDto();
        machineDto.setId(machine.getId());
        machineDto.setName(machine.getName());
        machineDto.setEnvironmentType(machine.getEnvironmentType());
        machineDto.setHostSshPort(machine.getHostSshPort());
        machineDto.setHostSshUsername(machine.getHostSshUsername());
        machineDto.setDeploymentPort(machine.getDeploymentPort());
        machineDto.setSshHost(machine.getSshHost());
        return machineDto;
    }
}

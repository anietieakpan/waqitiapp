import { HardhatRuntimeEnvironment } from "hardhat/types";
import { DeployFunction } from "hardhat-deploy/types";
import { ethers } from "hardhat";

const func: DeployFunction = async function (hre: HardhatRuntimeEnvironment) {
  const { deployments, getNamedAccounts, network } = hre;
  const { deploy } = deployments;
  const { deployer } = await getNamedAccounts();

  console.log("Deploying Layer 2 solutions with account:", deployer);

  // Deploy Optimistic Rollup
  const optimisticRollup = await deploy("OptimisticRollup", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            deployer, // admin
            86400, // challengePeriod (1 day)
            ethers.utils.parseEther("1"), // minimumStake
            ethers.utils.parseEther("10"), // sequencerBond
            3600, // withdrawalDelay (1 hour)
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("OptimisticRollup deployed to:", optimisticRollup.address);

  // Deploy ZK Rollup
  const zkRollup = await deploy("ZKRollup", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            deployer, // admin
            ethers.constants.AddressZero, // verifierContract (placeholder)
            1000, // maxTransactionsPerBlock
            300, // blockProductionTime (5 minutes)
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("ZKRollup deployed to:", zkRollup.address);

  // Deploy State Channel
  const stateChannel = await deploy("StateChannel", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            deployer, // admin
            86400, // defaultChallengePeriod (1 day)
            ethers.utils.parseEther("0.01"), // minChannelDeposit
            7776000, // maxChannelDuration (90 days)
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("StateChannel deployed to:", stateChannel.address);

  // Deploy Plasma Chain
  const plasmaChain = await deploy("PlasmaChain", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            deployer, // admin
            ethers.utils.parseEther("0.1"), // exitBond
            604800, // challengePeriod (1 week)
            86400, // exitDelay (1 day)
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("PlasmaChain deployed to:", plasmaChain.address);

  // Save deployment addresses for frontend
  const deploymentInfo = {
    network: network.name,
    chainId: network.config.chainId,
    contracts: {
      OptimisticRollup: optimisticRollup.address,
      ZKRollup: zkRollup.address,
      StateChannel: stateChannel.address,
      PlasmaChain: plasmaChain.address,
    },
    deployedAt: new Date().toISOString(),
  };

  console.log("Layer 2 deployment completed:", deploymentInfo);
};

export default func;
func.tags = ["Layer2", "Scaling"];
func.dependencies = ["WaqitiToken"];
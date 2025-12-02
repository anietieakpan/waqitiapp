import { HardhatRuntimeEnvironment } from "hardhat/types";
import { DeployFunction } from "hardhat-deploy/types";
import { ethers } from "hardhat";

const func: DeployFunction = async function (hre: HardhatRuntimeEnvironment) {
  const { deployments, getNamedAccounts, network } = hre;
  const { deploy } = deployments;
  const { deployer } = await getNamedAccounts();

  console.log("Deploying CrossChainBridge with account:", deployer);

  // Get chain ID
  const chainId = network.config.chainId || 31337;

  // Deploy bridge
  const bridge = await deploy("CrossChainBridge", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            deployer, // admin
            3, // requiredValidators
            ethers.utils.parseEther("0.001"), // bridgeFee
            deployer, // feeRecipient
            chainId, // currentChainId
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("CrossChainBridge deployed to:", bridge.address);

  // Configure supported chains (example)
  if (network.name === "localhost" || network.name === "hardhat") {
    const bridgeContract = await ethers.getContractAt("CrossChainBridge", bridge.address);
    
    // Add Ethereum mainnet
    await bridgeContract.addSupportedChain(
      1, // Ethereum mainnet
      12, // minConfirmations
      ethers.utils.parseEther("1000000") // dailyLimit
    );
    
    // Add Polygon
    await bridgeContract.addSupportedChain(
      137, // Polygon
      100, // minConfirmations
      ethers.utils.parseEther("1000000") // dailyLimit
    );
    
    // Add BSC
    await bridgeContract.addSupportedChain(
      56, // BSC
      15, // minConfirmations
      ethers.utils.parseEther("1000000") // dailyLimit
    );
    
    console.log("Configured supported chains");
  }
};

export default func;
func.tags = ["CrossChainBridge", "Bridge"];
func.dependencies = ["WaqitiToken"];
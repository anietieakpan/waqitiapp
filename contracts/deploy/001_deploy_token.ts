import { HardhatRuntimeEnvironment } from "hardhat/types";
import { DeployFunction } from "hardhat-deploy/types";
import { ethers } from "hardhat";

const func: DeployFunction = async function (hre: HardhatRuntimeEnvironment) {
  const { deployments, getNamedAccounts, network } = hre;
  const { deploy } = deployments;
  const { deployer } = await getNamedAccounts();

  console.log("Deploying WaqitiToken with account:", deployer);
  console.log("Network:", network.name);

  // Deploy implementation
  const waqitiToken = await deploy("WaqitiToken", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            "Waqiti Token", // name
            "WQT", // symbol
            deployer, // admin
            ethers.utils.parseEther("100000000"), // 100M initial supply
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("WaqitiToken deployed to:", waqitiToken.address);

  // Verify on etherscan if not local
  if (network.name !== "hardhat" && network.name !== "localhost") {
    try {
      await hre.run("verify:verify", {
        address: waqitiToken.address,
        constructorArguments: [],
      });
    } catch (error) {
      console.log("Verification failed:", error);
    }
  }
};

export default func;
func.tags = ["WaqitiToken", "Token"];
func.dependencies = [];
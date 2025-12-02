import { HardhatRuntimeEnvironment } from "hardhat/types";
import { DeployFunction } from "hardhat-deploy/types";
import { ethers } from "hardhat";

const func: DeployFunction = async function (hre: HardhatRuntimeEnvironment) {
  const { deployments, getNamedAccounts } = hre;
  const { deploy } = deployments;
  const { deployer, treasury } = await getNamedAccounts();

  console.log("Deploying WaqitiUSD with account:", deployer);

  const wusd = await deploy("WaqitiUSD", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            deployer, // admin
            300, // stabilityFee (3% APR)
            1000, // liquidationPenalty (10%)
            15000, // minCollateralRatio (150%)
            ethers.utils.parseEther("100000000"), // debtCeiling (100M)
            treasury || deployer, // treasury
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("WaqitiUSD deployed to:", wusd.address);
};

export default func;
func.tags = ["WaqitiUSD", "Stablecoin"];
func.dependencies = [];
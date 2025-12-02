import { HardhatRuntimeEnvironment } from "hardhat/types";
import { DeployFunction } from "hardhat-deploy/types";

const func: DeployFunction = async function (hre: HardhatRuntimeEnvironment) {
  const { deployments, getNamedAccounts } = hre;
  const { deploy } = deployments;
  const { deployer, treasury } = await getNamedAccounts();

  console.log("Deploying WaqitiLendingPool with account:", deployer);

  const lendingPool = await deploy("WaqitiLendingPool", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            deployer, // admin
            treasury || deployer, // treasury
            500, // liquidationIncentive (5%)
            10, // flashLoanFee (0.1%)
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("WaqitiLendingPool deployed to:", lendingPool.address);
};

export default func;
func.tags = ["WaqitiLendingPool", "DeFi"];
func.dependencies = [];
import { HardhatRuntimeEnvironment } from "hardhat/types";
import { DeployFunction } from "hardhat-deploy/types";

const func: DeployFunction = async function (hre: HardhatRuntimeEnvironment) {
  const { deployments, getNamedAccounts } = hre;
  const { deploy } = deployments;
  const { deployer, treasury } = await getNamedAccounts();

  console.log("Deploying WaqitiAMM with account:", deployer);

  const amm = await deploy("WaqitiAMM", {
    from: deployer,
    proxy: {
      proxyContract: "OpenZeppelinTransparentProxy",
      execute: {
        init: {
          methodName: "initialize",
          args: [
            deployer, // admin
            30, // swapFee (0.3%)
            5, // protocolFee (0.05%)
            treasury || deployer, // feeRecipient
          ],
        },
      },
    },
    log: true,
    autoMine: true,
  });

  console.log("WaqitiAMM deployed to:", amm.address);
};

export default func;
func.tags = ["WaqitiAMM", "DeFi"];
func.dependencies = ["WaqitiToken"];
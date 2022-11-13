pipeline {
    agent any
    parameters {
        string(name: 'vpc_name', defaultValue: 'mfq-tf-custom-vpc', description: '')
        string(name: 'vpc_cidr', defaultValue: '10.100.0.0/16', description: '')
        string(name: 'private_subnet1_cidr', defaultValue: '10.100.1.0/24', description: '')
        string(name: 'private_subnet2_cidr', defaultValue: '10.100.2.0/24', description: '')
        //string(name: 'private_subnet3_cidr', defaultValue: '', description: '') // descomentar esta línea para desplegar una subred privada en la tercera zona de disponibilidad
        string(name: 'public_subnet1_cidr', defaultValue: '10.100.3.0/24', description: '')
        string(name: 'public_subnet2_cidr', defaultValue: '10.100.4.0/24', description: '')
        // string(name: 'public_subnet3_cidr', defaultValue: '', description: '') // descomentar esta línea para desplegar una subred pública en la tercera zona de disponibilidad
        booleanParam(name: 'autoApprove', defaultValue: false, description: '')
        booleanParam(name: 'destroy', defaultValue: false, description: '')
    }
    tools {
       terraform 'terraform'
    }
    stages {
        stage('check parameters') {
           steps{
                script {
                    if (params.vpc_name != '') {
                        if (params.vpc_cidr == '' || params.private_subnet1_cidr == '' || params.private_subnet2_cidr == '' || params.public_subnet1_cidr == '' || params.public_subnet2_cidr == '') {
                            currentBuild.result = 'ABORTED'
                            error('Stopping early…')
                        } else {
                            echo "ALL OK, EXECUTING WITH PARAMETERS"
                        }
                        
                    }  else {
                        echo "ALL OK, EXECUTING WITHOUT PARAMETERS"
                    }
                }
            }
        }
    
        stage('git checkout') {
           steps{
                git branch: 'main', credentialsId: 'mfq-git-jenkins-user', url: 'https://github.com/mfqat/mfq-aws-vpc.git'
            }
        }

        stage('terraform format') {
            steps{
                sh 'terraform fmt'
            }
        }

        stage('terraform init') {
            steps{
                withAWS(credentials: 'mfq-aws-creds', region: 'eu-west-1') {                    
                    sh 'terraform init -reconfigure'
                }
            }
        }

        stage('terraform plan with parameters') {
            when { expression { params.destroy == false && params.vpc_name != '' } }
            steps{                
                withAWS(credentials: 'mfq-aws-creds', region: 'eu-west-1') {
                    sh 'terraform plan -var vpc_name=' + params.vpc_name + ' -var vpc_cidr=' + params.vpc_cidr + ' -var private_subnet1_cidr=' + params.private_subnet1_cidr + ' -var private_subnet2_cidr=' + params.private_subnet2_cidr + ' -var public_subnet1_cidr=' + params.public_subnet1_cidr + ' -var public_subnet2_cidr=' + params.public_subnet2_cidr
                    
                }
            }
        }
        
        stage('terraform plan without parameters') {
            when { expression { params.destroy == false && params.vpc_name == '' } }
            steps{                
                withAWS(credentials: 'mfq-aws-creds', region: 'eu-west-1') {
                    sh 'terraform plan'
                }
            }
        }

        stage('approval'){
            when { expression { params.autoApprove == false } }
            steps {
                script {
                    env.CONTINUE = input message: 'User input required',
                    parameters: [choice(name: 'Apply', choices: 'no\nyes', description: 'Choose "yes" if you want to apply this plan')]
                }
            }
        }
        
        stage('terraform apply with parameters') {
            
            when { 
                allOf {
                     expression { params.destroy != true } 
                     expression { params.vpc_name != '' }
                     expression { params.autoApprove != false || env.CONTINUE != 'no' }
                 } 
             } 
            steps{
                withAWS(credentials: 'mfq-aws-creds', region: 'eu-west-1') {
                    sh 'terraform apply --auto-approve -var vpc_name=' + params.vpc_name + ' -var vpc_cidr=' + params.vpc_cidr + ' -var private_subnet1_cidr=' + params.private_subnet1_cidr + ' -var private_subnet2_cidr=' + params.private_subnet2_cidr + ' -var public_subnet1_cidr=' + params.public_subnet1_cidr + ' -var public_subnet2_cidr=' + params.public_subnet2_cidr
                }
            }
        }
        
        stage('terraform apply without parameters') {
            when { 
                allOf {
                     expression { params.destroy != true } 
                     expression { params.vpc_name == '' } 
                     expression { params.autoApprove != false || env.CONTINUE != 'no' } 
                 } 
             }

            steps{
                withAWS(credentials: 'mfq-aws-creds', region: 'eu-west-1') {
                            sh 'terraform apply --auto-approve'
                        }
                
            }
        }

        stage('terraform destroy') {
            when { expression { params.destroy == true && env.CONTINUE != 'no' } }
            steps{
                withAWS(credentials: 'mfq-aws-creds', region: 'eu-west-1') {
                    sh 'terraform destroy --auto-approve'
                }
            }
            
        }
    }
    
}


